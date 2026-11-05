/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonWriter
import io.github.yewplay.core.designsystem.YpDialogCard
import io.github.yewplay.core.designsystem.YpPillButton
import io.github.yewplay.core.designsystem.YpScreenMargin
import io.github.yewplay.core.designsystem.YpTextField
import io.github.yewplay.core.designsystem.YpTheme
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.KEY_MAIN_PAGE_CHANGE
import org.schabi.newpipe.util.PeertubeHelper
import org.schabi.newpipe.util.ThemeHelper

/**
 * YewPlay: the PeerTube instances, in Compose: tap one to use it, arrows to reorder, ✕ to
 * remove (not the one in use), "Add instance" by its address and "Restore defaults". The list
 * is saved when the screen is left.
 */
class PeertubeInstanceListFragment : Fragment() {
    private lateinit var prefs: SharedPreferences
    private lateinit var listKey: String
    private val disposables = CompositeDisposable()

    private val instances = mutableStateListOf<PeertubeInstance>()
    private var selectedUrl by mutableStateOf("")
    private var loading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        listKey = getString(R.string.peertube_instance_list_key)
        selectedUrl = PeertubeHelper.currentInstance.url
        instances.addAll(PeertubeHelper.getInstanceList(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YpTheme(themeKey, nightThemeKey) { InstancesScreen() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.peertube_instance_url_title))
    }

    override fun onPause() {
        super.onPause()
        saveChanges()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    @Composable
    private fun InstancesScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                modifier = Modifier.padding(horizontal = YpScreenMargin, vertical = 12.dp),
                text = stringResource(R.string.peertube_instance_url_help, stringResource(R.string.peertube_instance_list_url)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(instances, key = { _, it -> it.url }) { index, instance ->
                    InstanceRow(index, instance)
                    HorizontalDivider(
                        modifier = Modifier.padding(start = YpScreenMargin + 40.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = YpScreenMargin, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                YpPillButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.peertube_instance_add_title),
                    primary = true,
                    enabled = !loading,
                    onClick = ::showAddDialog
                )
                YpPillButton(
                    label = stringResource(R.string.restore_defaults),
                    primary = false,
                    onClick = ::confirmRestoreDefaults
                )
            }
        }
    }

    @Composable
    private fun InstanceRow(index: Int, instance: PeertubeInstance) {
        val selected = instance.url == selectedUrl
        val colors = MaterialTheme.colorScheme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { select(instance) }
                .padding(start = YpScreenMargin, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        modifier = Modifier.size(14.dp),
                        painter = painterResource(R.drawable.ic_done),
                        contentDescription = null,
                        tint = colors.onPrimary
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(text = instance.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = instance.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(enabled = index > 0, onClick = { move(index, index - 1) }) {
                Icon(painter = painterResource(R.drawable.ic_arrow_drop_up), contentDescription = stringResource(R.string.yp_tabs_move_up))
            }
            IconButton(enabled = index < instances.lastIndex, onClick = { move(index, index + 1) }) {
                Icon(painter = painterResource(R.drawable.ic_arrow_drop_down), contentDescription = stringResource(R.string.yp_tabs_move_down))
            }
            IconButton(enabled = !selected, onClick = { instances.removeAt(index) }) {
                Icon(painter = painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.delete))
            }
        }
    }

    private fun move(from: Int, to: Int) {
        if (to in instances.indices) {
            instances.add(to, instances.removeAt(from))
        }
    }

    private fun select(instance: PeertubeInstance) {
        selectedUrl = PeertubeHelper.selectInstance(instance, requireContext()).url
        prefs.edit { putBoolean(KEY_MAIN_PAGE_CHANGE, true) }
    }

    private fun saveChanges() {
        val writer = JsonWriter.string().`object`().array("instances")
        instances.forEach { instance ->
            writer.`object`()
            writer.value("name", instance.name)
            writer.value("url", instance.url)
            writer.end()
        }
        val json = writer.end().end().done()
        prefs.edit { putString(listKey, json) }
    }

    private fun confirmRestoreDefaults() {
        val context = requireContext()
        YpDialogs.card(context) { dismiss ->
            YpDialogCard(
                title = stringResource(R.string.restore_defaults),
                message = stringResource(R.string.restore_defaults_confirmation),
                confirmLabel = stringResource(R.string.ok),
                onConfirm = {
                    prefs.edit { remove(listKey) }
                    select(PeertubeInstance.DEFAULT_INSTANCE)
                    instances.clear()
                    instances.addAll(PeertubeHelper.getInstanceList(context))
                    dismiss()
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = dismiss
            )
        }.show()
    }

    private fun showAddDialog() {
        YpDialogs.card(requireContext(), showKeyboard = true) { dismiss ->
            var url by rememberSaveable { mutableStateOf("") }
            YpDialogCard(
                title = stringResource(R.string.peertube_instance_add_title),
                confirmLabel = stringResource(R.string.ok),
                confirmEnabled = url.isNotBlank(),
                onConfirm = {
                    dismiss()
                    addInstance(url)
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = dismiss
            ) {
                YpTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = stringResource(R.string.peertube_instance_add_help),
                    autoFocus = true,
                    keyboardType = KeyboardType.Uri,
                    onDone = {
                        if (url.isNotBlank()) {
                            dismiss()
                            addInstance(url)
                        }
                    }
                )
            }
        }.show()
    }

    private fun addInstance(url: String) {
        val clean = cleanUrl(url) ?: return
        loading = true
        disposables.add(
            Single.fromCallable { PeertubeInstance(clean).apply { fetchInstanceMetaData() } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { instance ->
                        loading = false
                        instances.add(instance)
                    },
                    {
                        loading = false
                        Toast.makeText(activity, R.string.peertube_instance_add_fail, Toast.LENGTH_SHORT).show()
                    }
                )
        )
    }

    /** "https://" when there is no protocol, no trailing slash; only https and new ones. */
    private fun cleanUrl(url: String): String? {
        var clean = url.trim()
        if (!clean.startsWith("http")) {
            clean = "https://$clean"
        }
        clean = clean.removeSuffix("/")
        if (!clean.startsWith("https://")) {
            Toast.makeText(activity, R.string.peertube_instance_add_https_only, Toast.LENGTH_SHORT).show()
            return null
        }
        if (instances.any { it.url == clean }) {
            Toast.makeText(activity, R.string.peertube_instance_add_exists, Toast.LENGTH_SHORT).show()
            return null
        }
        return clean
    }
}
