/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.error

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NavUtils
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTheme
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.ThemeHelper

/**
 * YewPlay: the reCAPTCHA challenge of a service, in Compose: the page of the challenge in a web
 * view and "Done" to keep the cookies it gave and go back. Back does the same.
 */
class ReCaptchaActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var foundCookies = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        val url = sanitizeRecaptchaUrl(intent.getStringExtra(RECAPTCHA_URL_EXTRA))
        // cancelled unless cookies are found
        setResult(RESULT_CANCELED)

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.userAgentString = DownloaderImpl.USER_AGENT
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (MainActivity.DEBUG) {
                        Log.d(TAG, "shouldOverrideUrlLoading: url=" + request.url)
                    }
                    handleCookiesFromUrl(request.url.toString())
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    handleCookiesFromUrl(url)
                }
            }
            // clean the cache, history and cookies of the web view
            clearCache(true)
            clearHistory()
        }
        CookieManager.getInstance().removeAllCookies(null)
        web.loadUrl(url)
        webView = web

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = saveCookiesAndFinish()
            }
        )

        val themeKey = YpThemePrefs.themeKey(this)
        val nightThemeKey = YpThemePrefs.nightThemeKey(this)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    YpTheme(themeKey, nightThemeKey) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = YpScreenMargin, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.title_activity_recaptcha),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = stringResource(R.string.subtitle_activity_recaptcha),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                YpPillButton(
                                    label = stringResource(R.string.done),
                                    primary = true,
                                    onClick = ::saveCookiesAndFinish
                                )
                            }
                            AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        )
    }

    private fun saveCookiesAndFinish() {
        // try to get the cookies of the page still open
        handleCookiesFromUrl(webView?.url)
        if (MainActivity.DEBUG) {
            Log.d(TAG, "saveCookiesAndFinish: foundCookies=$foundCookies")
        }
        if (foundCookies.isNotEmpty()) {
            // save the cookies and give them to the downloader
            PreferenceManager.getDefaultSharedPreferences(applicationContext).edit {
                putString(applicationContext.getString(R.string.recaptcha_cookies_key), foundCookies)
            }
            DownloaderImpl.getInstance().setCookie(RECAPTCHA_COOKIES_KEY, foundCookies)
            setResult(RESULT_OK)
        }
        // a blank page unloads YouTube, so that nothing keeps playing in the background
        webView?.loadUrl("about:blank")
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        NavUtils.navigateUpTo(this, intent)
    }

    private fun handleCookiesFromUrl(url: String?) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "handleCookiesFromUrl: url=$url")
        }
        if (url == null) {
            return
        }
        handleCookies(CookieManager.getInstance().getCookie(url))
        // sometimes the cookies are inside the url
        val abuseStart = url.indexOf("google_abuse=")
        if (abuseStart != -1) {
            val abuseEnd = url.indexOf("+path")
            try {
                handleCookies(Utils.decodeUrlUtf8(url.substring(abuseStart + 13, abuseEnd)))
            } catch (e: StringIndexOutOfBoundsException) {
                if (MainActivity.DEBUG) {
                    Log.e(TAG, "handleCookiesFromUrl: invalid google abuse from $abuseStart to $abuseEnd in $url", e)
                }
            }
        }
    }

    private fun handleCookies(cookies: String?) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "handleCookies: cookies=$cookies")
        }
        if (cookies == null) {
            return
        }
        addYoutubeCookies(cookies)
        // methods to extract the cookies of other services go here
    }

    private fun addYoutubeCookies(cookies: String) {
        val isYoutube = cookies.contains("s_gl=") || cookies.contains("goojf=") ||
            cookies.contains("VISITOR_INFO1_LIVE=") || cookies.contains("GOOGLE_ABUSE_EXEMPTION=")
        if (isYoutube) {
            // YouTube seems to also need the other cookies
            addCookie(cookies)
        }
    }

    private fun addCookie(cookie: String) {
        if (foundCookies.contains(cookie)) {
            return
        }
        foundCookies += when {
            foundCookies.isEmpty() || foundCookies.endsWith("; ") -> cookie
            foundCookies.endsWith(";") -> " $cookie"
            else -> "; $cookie"
        }
    }

    companion object {
        const val RECAPTCHA_REQUEST = 10
        const val RECAPTCHA_URL_EXTRA = "recaptcha_url_extra"
        const val YT_URL = "https://www.youtube.com"
        const val RECAPTCHA_COOKIES_KEY = "recaptcha_cookies"

        @JvmField
        val TAG: String = ReCaptchaActivity::class.java.toString()

        @JvmStatic
        fun sanitizeRecaptchaUrl(url: String?): String = if (url.isNullOrBlank()) {
            YT_URL // YouTube is the most likely service to have thrown a recaptcha
        } else {
            // remove the "pbj=1" parameter of YouTube urls, as it makes the page JSON and not HTML
            url.replace("&pbj=1", "").replace("pbj=1&", "").replace("?pbj=1", "")
        }
    }
}
