/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.github.yewplay.core.designsystem.YpSheet
import io.github.yewplay.core.designsystem.YpSheetItem
import io.github.yewplay.core.designsystem.YpSheetTitle
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.local.subscription.SubscriptionsImportExportHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper

/** YewPlay: the ways to import (and export) subscriptions, as a Compose bottom sheet. */
object YpSubscriptionImport {

    /**
     * Asks where to import from: a NewPipe export file, or a service that can export its
     * subscriptions (YouTube, SoundCloud...). With [includeExport] it also offers to export
     * them to a file.
     *
     * @param fragment the screen asking
     * @param helper created by that screen in onCreate (it registers for activity results)
     * @param fragmentManager to open the import from a service
     */
    @JvmOverloads
    fun showChooser(
        fragment: Fragment,
        helper: SubscriptionsImportExportHelper,
        fragmentManager: FragmentManager,
        includeExport: Boolean = false
    ) {
        val services = ServiceList.all().filter { service ->
            service.subscriptionExtractor?.supportedSources?.isNotEmpty() == true
        }
        YpDialogs.sheet(fragment.requireContext()) { dismiss ->
            YpSheet {
                YpSheetTitle(stringResource(R.string.import_from))
                YpSheetItem(
                    label = stringResource(R.string.yp_subs_import_previous),
                    icon = painterResource(R.drawable.ic_backup),
                    onClick = {
                        dismiss()
                        helper.onImportPreviousSelected()
                    }
                )
                services.forEach { service ->
                    YpSheetItem(
                        label = service.serviceInfo.name,
                        icon = painterResource(ServiceHelper.getIcon(service.serviceId)),
                        onClick = {
                            dismiss()
                            NavigationHelper.openSubscriptionsImportFragment(fragmentManager, service.serviceId)
                        }
                    )
                }
                if (includeExport) {
                    YpSheetTitle(stringResource(R.string.export_to))
                    YpSheetItem(
                        label = stringResource(R.string.file),
                        icon = painterResource(R.drawable.ic_save),
                        onClick = {
                            dismiss()
                            helper.onExportSelected()
                        }
                    )
                }
            }
        }.show()
    }
}
