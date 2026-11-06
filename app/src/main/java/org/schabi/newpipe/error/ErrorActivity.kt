/*
 * SPDX-FileCopyrightText: 2015-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.error

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.grack.nanojson.JsonWriter
import io.github.yewplay.core.designsystem.YpDialogCard
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTextField
import io.github.yewplay.core.designsystem.YpTheme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * This activity is used to show error details and allow reporting them in various ways.
 * Use [ErrorUtil.openActivity] to correctly open this activity.
 *
 * YewPlay: drawn in Compose; the reports go to the issues of YewPlay on GitHub (copy the
 * report, then open the issue page), or are shared as JSON.
 */
class ErrorActivity : AppCompatActivity() {
    private lateinit var errorInfo: ErrorInfo
    private lateinit var currentTimeStamp: String

    private val contentCountryString: String
        get() = Localization.getPreferredContentCountry(this).countryCode

    private val contentLanguageString: String
        get() = Localization.getPreferredLocalization(this).localizationCode

    private val appLanguage: String
        get() = Localization.getAppLocale().toString()

    private val osString: String
        get() {
            val name = System.getProperty("os.name")!!
            val osBase = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.BASE_OS.ifEmpty { "Android" }
            } else {
                "Android"
            }
            return "$name $osBase ${Build.VERSION.RELEASE} - ${Build.VERSION.SDK_INT}"
        }

    private var comment by mutableStateOf("")

    // ---- Activity lifecycle ----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.setDayNightMode(this)
        ThemeHelper.setTheme(this)

        errorInfo = IntentCompat.getParcelableExtra(intent, ERROR_INFO, ErrorInfo::class.java)!!
        // print current time, as zoned ISO8601 timestamp
        currentTimeStamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        comment = savedInstanceState?.getString(KEY_COMMENT).orEmpty()

        val themeKey = YpThemePrefs.themeKey(this)
        val nightThemeKey = YpThemePrefs.nightThemeKey(this)
        setContentView(
            ComposeView(this).apply {
                setContent { YpTheme(themeKey, nightThemeKey) { ErrorScreen() } }
            }
        )

        // print stack trace once again for debugging:
        errorInfo.stackTraces.forEach { Log.e(TAG, it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_COMMENT, comment)
    }

    // ---- UI ----

    @Composable
    private fun ErrorScreen() {
        val colors = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = ::shareReport) {
                    Icon(painter = painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.share))
                }
            }
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin),
                text = stringResource(R.string.error_report_title),
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 12.dp),
                // the guru meditation is an easter egg of NewPipe
                text = stringResource(R.string.sorry_string) + "\n" + stringResource(R.string.guru_meditation),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Headline(stringResource(R.string.what_happened_headline))
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin),
                text = errorInfo.getMessage(this@ErrorActivity).toString(),
                style = MaterialTheme.typography.bodyLarge
            )
            Headline(stringResource(R.string.what_device_headline))
            val labels = stringResource(R.string.info_labels).split("\n")
            infoValues().forEachIndexed { index, value ->
                Row(modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 2.dp)) {
                    Text(
                        modifier = Modifier.weight(0.45f),
                        text = labels.getOrElse(index) { "" },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(modifier = Modifier.weight(0.55f), text = value, style = MaterialTheme.typography.bodySmall)
                }
            }
            Headline(stringResource(R.string.error_details_headline))
            Box(
                modifier = Modifier
                    .padding(horizontal = YpScreenMargin)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainer)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = formErrorText(errorInfo.stackTraces),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
            Headline(stringResource(R.string.your_comment))
            YpTextField(
                value = comment,
                onValueChange = { comment = it },
                singleLine = false,
                horizontalPadding = YpScreenMargin
            )
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 16.dp),
                text = stringResource(R.string.error_report_open_github_notice),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Row(
                modifier = Modifier.padding(horizontal = YpScreenMargin),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                YpPillButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.copy_for_github),
                    primary = false,
                    onClick = { ShareUtils.copyToClipboard(this@ErrorActivity, buildMarkdown()) }
                )
                YpPillButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.error_report_open_issue_button_text),
                    primary = true,
                    onClick = ::openPrivacyPolicyDialog
                )
            }
        }
    }

    @Composable
    private fun Headline(text: String) {
        Text(
            modifier = Modifier.padding(start = YpScreenMargin, end = YpScreenMargin, top = 20.dp, bottom = 6.dp),
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
    }

    private fun shareReport() {
        ShareUtils.shareText(applicationContext, getString(R.string.error_report_title), buildJson())
    }

    /** Asks to accept the privacy policy before opening the issue page of YewPlay on GitHub. */
    private fun openPrivacyPolicyDialog() {
        YpDialogs.card(this) { dismiss ->
            YpDialogCard(
                title = stringResource(R.string.privacy_policy_title),
                message = stringResource(R.string.start_accept_privacy_policy),
                confirmLabel = stringResource(R.string.accept),
                onConfirm = {
                    dismiss()
                    ShareUtils.openUrlInApp(this@ErrorActivity, ERROR_GITHUB_ISSUE_URL)
                },
                dismissLabel = stringResource(R.string.decline),
                onDismiss = dismiss
            ) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clickable {
                            ShareUtils.openUrlInApp(this@ErrorActivity, getString(R.string.privacy_policy_url))
                        },
                    text = stringResource(R.string.read_privacy_policy),
                    style = MaterialTheme.typography.labelLarge,
                    textDecoration = TextDecoration.Underline
                )
            }
        }.apply { setCancelable(false) }.show()
    }

    private fun formErrorText(stacktrace: Array<String>): String {
        val separator = "-------------------------------------"
        return stacktrace.joinToString(separator + "\n", separator + "\n", separator)
    }

    private fun infoValues(): List<String> = listOf(
        errorInfo.userAction.message,
        errorInfo.request,
        contentLanguageString,
        contentCountryString,
        appLanguage,
        errorInfo.getServiceName(),
        currentTimeStamp,
        packageName,
        BuildConfig.VERSION_NAME,
        osString
    )

    private fun buildJson(): String {
        try {
            return JsonWriter.string()
                .`object`()
                .value("user_action", errorInfo.userAction.message)
                .value("request", errorInfo.request)
                .value("content_language", contentLanguageString)
                .value("content_country", contentCountryString)
                .value("app_language", appLanguage)
                .value("service", errorInfo.getServiceName())
                .value("package", packageName)
                .value("version", BuildConfig.VERSION_NAME)
                .value("os", osString)
                .value("time", currentTimeStamp)
                .array("exceptions", errorInfo.stackTraces.toList())
                .value("user_comment", comment)
                .end()
                .done()
        } catch (exception: Exception) {
            Log.e(TAG, "Error while erroring: Could not build json", exception)
        }

        return ""
    }

    private fun buildMarkdown(): String {
        try {
            return buildString(1024) {
                val userComment = comment
                if (userComment.isNotEmpty()) {
                    appendLine(userComment)
                }

                // basic error info
                appendLine("## Exception")
                appendLine("* __User Action:__ ${errorInfo.userAction.message}")
                appendLine("* __Request:__ ${errorInfo.request}")
                appendLine("* __Content Country:__ $contentCountryString")
                appendLine("* __Content Language:__ $contentLanguageString")
                appendLine("* __App Language:__ $appLanguage")
                appendLine("* __Service:__ ${errorInfo.getServiceName()}")
                appendLine("* __Timestamp:__ $currentTimeStamp")
                appendLine("* __Package:__ $packageName")
                appendLine("* __Service:__ ${errorInfo.getServiceName()}")
                appendLine("* __Version:__ ${BuildConfig.VERSION_NAME}")
                appendLine("* __OS:__ $osString")

                // Collapse all logs to a single paragraph when there are more than one
                // to keep the GitHub issue clean.
                if (errorInfo.stackTraces.size > 1) {
                    append("<details><summary><b>Exceptions (")
                    append(errorInfo.stackTraces.size)
                    append(")</b></summary><p>\n")
                }

                // add the logs
                errorInfo.stackTraces.forEachIndexed { index, stacktrace ->
                    append("<details><summary><b>Crash log ")
                    if (errorInfo.stackTraces.size > 1) {
                        append(index + 1)
                    }
                    append("</b>")
                    append("</summary><p>\n")
                    append("\n```\n${stacktrace}\n```\n")
                    append("</details>\n")
                }

                // make sure to close everything
                if (errorInfo.stackTraces.size > 1) {
                    append("</p></details>\n")
                }

                append("<hr>\n")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Error while erroring: Could not build markdown", exception)
            return ""
        }
    }

    companion object {
        // LOG TAGS
        private val TAG = ErrorActivity::class.java.toString()

        // BUNDLE TAGS
        const val ERROR_INFO = "error_info"

        private const val KEY_COMMENT = "comment"

        private const val ERROR_GITHUB_ISSUE_URL = "https://github.com/juscelino25bezerra-art/Yewplay/issues"
    }
}
