/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTheme
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.yewplay.YpRichText
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * YewPlay: the description of a video (a tab of the video screen), in Compose: the date, the
 * description with its links (or selectable text), the details of the video and its tags.
 */
class DescriptionFragment() : BaseFragment() {
    private lateinit var info: StreamInfo

    constructor(info: StreamInfo) : this() {
        arguments = bundleOf(KEY_INFO to info)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        info = BundleCompat.getSerializable(requireArguments(), KEY_INFO, StreamInfo::class.java)!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) { DescriptionScreen() }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun DescriptionScreen() {
        val colors = MaterialTheme.colorScheme
        val description = info.description ?: Description.EMPTY_DESCRIPTION
        var selecting by rememberSaveable { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = YpScreenMargin, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = info.uploadDate?.let { Localization.localizeUploadDate(requireContext(), it.offsetDateTime()) }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                if (description != Description.EMPTY_DESCRIPTION) {
                    IconButton(onClick = { selecting = !selecting }) {
                        Icon(
                            painter = painterResource(if (selecting) R.drawable.ic_close else R.drawable.ic_select_all),
                            contentDescription = stringResource(
                                if (selecting) R.string.description_select_disable else R.string.description_select_enable
                            )
                        )
                    }
                }
            }
            if (selecting) {
                Text(
                    modifier = Modifier.padding(bottom = 8.dp),
                    text = stringResource(R.string.description_select_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (description != Description.EMPTY_DESCRIPTION) {
                YpRichText(
                    description = description,
                    serviceId = info.serviceId,
                    streamUrl = info.url,
                    selectable = selecting
                )
            }
            Metadata()
            val tags = info.tags.orEmpty().sortedWith(String.CASE_INSENSITIVE_ORDER)
            if (tags.isNotEmpty()) {
                Label(R.string.metadata_tags)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag -> TagChip(tag) }
                }
            }
        }
    }

    @Composable
    private fun Metadata() {
        Item(R.string.metadata_category, info.category)
        Item(R.string.metadata_licence, info.licence)
        privacy()?.let { Item(R.string.metadata_privacy, stringResource(it)) }
        if (info.ageLimit != StreamExtractor.NO_AGE_LIMIT) {
            Item(R.string.metadata_age_limit, info.ageLimit.toString())
        }
        info.languageInfo?.let { Item(R.string.metadata_language, it.getDisplayLanguage(Localization.getAppLocale())) }
        Item(R.string.metadata_support, info.supportInfo, linkify = true)
        Item(R.string.metadata_host, info.host, linkify = true)
        Images(R.string.metadata_thumbnails, info.thumbnails)
        Images(R.string.metadata_uploader_avatars, info.uploaderAvatars)
        Images(R.string.metadata_subchannel_avatars, info.subChannelAvatars)
    }

    private fun privacy(): Int? = when (info.privacy) {
        StreamExtractor.Privacy.PUBLIC -> R.string.metadata_privacy_public
        StreamExtractor.Privacy.UNLISTED -> R.string.metadata_privacy_unlisted
        StreamExtractor.Privacy.PRIVATE -> R.string.metadata_privacy_private
        StreamExtractor.Privacy.INTERNAL -> R.string.metadata_privacy_internal
        else -> null
    }

    @Composable
    private fun Label(@StringRes text: Int) {
        Text(
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            text = stringResource(text),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    /** A detail of the video; a long press on its name copies it. */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Item(@StringRes type: Int, content: String?, linkify: Boolean = false) {
        if (content.isNullOrBlank()) {
            return
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { ShareUtils.copyToClipboard(requireContext(), content) })
        ) {
            Label(type)
            if (linkify) {
                YpRichText(
                    description = Description(content, Description.Type.PLAIN_TEXT),
                    serviceId = info.serviceId,
                    streamUrl = null
                )
            } else {
                Text(text = content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    /** The sizes of an image, each one opening it; the one the app uses in bold. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Images(@StringRes type: Int, images: List<Image>?) {
        val preferred = ImageStrategy.choosePreferredImage(images.orEmpty()) ?: return
        Label(type)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            images.orEmpty().forEach { image ->
                Text(
                    modifier = Modifier.clickable { ShareUtils.openUrlInBrowser(requireContext(), image.url) },
                    text = imageLabel(image),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = if (image.url == preferred) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    private fun imageLabel(image: Image): String {
        fun size(value: Int) = if (value < 0) getString(R.string.question_mark) else value.toString()
        val known = image.height != Image.HEIGHT_UNKNOWN || image.width != Image.WIDTH_UNKNOWN ||
            image.estimatedResolutionLevel == Image.ResolutionLevel.UNKNOWN
        return if (known) {
            size(image.width) + "x" + size(image.height)
        } else {
            when (image.estimatedResolutionLevel) {
                Image.ResolutionLevel.LOW -> getString(R.string.image_quality_low)
                Image.ResolutionLevel.MEDIUM -> getString(R.string.image_quality_medium)
                else -> getString(R.string.image_quality_high)
            }
        }
    }

    /** A tag: tapping searches it, a long press copies it. */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun TagChip(tag: String) {
        Text(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .combinedClickable(
                    onClick = {
                        parentFragment?.let {
                            NavigationHelper.openSearchFragment(it.parentFragmentManager, info.serviceId, tag)
                        }
                    },
                    onLongClick = { ShareUtils.copyToClipboard(requireContext(), tag) }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            text = tag,
            style = MaterialTheme.typography.labelLarge
        )
    }

    private companion object {
        const val KEY_INFO = "stream_info"
    }
}
