/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import io.github.yewplay.core.designsystem.YpDialogCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.subscription.SubscriptionsImportExportHelper
import org.schabi.newpipe.local.yewplay.YpDialogs
import org.schabi.newpipe.settings.compose.YpPrefBuilder
import org.schabi.newpipe.settings.compose.YpPreferenceFragment
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ZipHelper

/**
 * YewPlay: backup and restore, in Compose: import or export the database and the settings,
 * reset the settings, and export or import the subscriptions.
 */
class BackupRestoreSettingsFragment : YpPreferenceFragment() {
    override val titleRes = R.string.settings_category_backup_restore_title

    private val exportDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private lateinit var manager: ImportExportManager
    private lateinit var importExportHelper: SubscriptionsImportExportHelper

    private val importLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult(), ::onImportPicked)
    private val exportLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult(), ::onExportPicked)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ImportExportManager(BackupFileLocator(requireContext()))
        // registers for the result of the file pickers, so it must be created here
        importExportHelper = SubscriptionsImportExportHelper(this)
    }

    override fun YpPrefBuilder.buildItems() {
        action(title = R.string.import_data_title, summary = R.string.import_data_summary, key = R.string.import_data) {
            NoFileManagerSafeGuard.launchSafe(
                importLauncher,
                StoredFileHelper.getPicker(requireContext(), ZIP_MIME_TYPE, lastDataUri()),
                TAG,
                context
            )
        }
        action(title = R.string.export_data_title, summary = R.string.export_data_summary, key = R.string.export_data) {
            NoFileManagerSafeGuard.launchSafe(
                exportLauncher,
                StoredFileHelper.getNewPicker(
                    requireContext(),
                    "YewPlayData-" + exportDateFormat.format(Date()) + ".zip",
                    ZIP_MIME_TYPE,
                    lastDataUri()
                ),
                TAG,
                context
            )
        }
        action(title = R.string.reset_settings_title, summary = R.string.reset_settings_summary, key = R.string.reset_settings) {
            confirm(R.string.reset_all_settings) {
                // delete every setting, then restart the app
                prefs.edit { clear() }
                activity?.let { NavigationHelper.restartApp(it) }
            }
        }
        action(
            title = R.string.export_subscriptions_title,
            summary = R.string.export_subscriptions_summary,
            key = R.string.export_subscriptions_key
        ) { importExportHelper.onExportSelected() }
        action(
            title = R.string.import_subscriptions_title,
            summary = R.string.import_subscriptions_summary,
            key = R.string.import_subscriptions_key
        ) { importExportHelper.onImportPreviousSelected() }
    }

    private fun onExportPicked(result: ActivityResult) {
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            return
        }
        val file = StoredFileHelper(requireContext(), uri, ZIP_MIME_TYPE)
        val executor = Executors.newSingleThreadExecutor()
        try {
            // checkpoint before the export
            executor.submit(Runnable { NewPipeDatabase.checkpoint() }).get()
            manager.exportDatabase(prefs, file)
            saveLastDataUri(uri) // only on success
            Toast.makeText(requireContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e, "Exporting database and settings")
        } finally {
            executor.shutdown()
        }
    }

    private fun onImportPicked(result: ActivityResult) {
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            return
        }
        val file = StoredFileHelper(requireContext(), uri, ZIP_MIME_TYPE)
        confirm(R.string.override_current_data) { importDatabase(file, uri) }
    }

    private fun importDatabase(file: StoredFileHelper, uri: Uri) {
        if (!ZipHelper.isValidZipFile(file)) {
            Toast.makeText(requireContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            manager.ensureDbDirectoryExists()
            // replace the current database
            if (!manager.extractDb(file)) {
                Toast.makeText(requireContext(), R.string.could_not_import_all_files, Toast.LENGTH_LONG).show()
            }
            // when the backup has settings, ask whether to import them too
            val hasJsonPrefs = manager.exportHasJsonPrefs(file)
            if (hasJsonPrefs || manager.exportHasSerializedPrefs(file)) {
                askImportSettings(file, uri, hasJsonPrefs)
            } else {
                finishImport(uri)
            }
        } catch (e: Exception) {
            showError(e, "Importing database and settings")
        }
    }

    private fun askImportSettings(file: StoredFileHelper, uri: Uri, hasJsonPrefs: Boolean) {
        val context = requireContext()
        YpDialogs.card(context) { dismiss ->
            YpDialogCard(
                title = stringResource(R.string.import_settings),
                message = if (hasJsonPrefs) null else stringResource(R.string.import_settings_vulnerable_format),
                confirmLabel = stringResource(R.string.ok),
                onConfirm = {
                    dismiss()
                    try {
                        if (hasJsonPrefs) {
                            manager.loadJsonPrefs(file, prefs)
                        } else {
                            manager.loadSerializedPrefs(file, prefs)
                        }
                        cleanImport(context, prefs)
                    } catch (e: Exception) {
                        ErrorUtil.createNotification(
                            context,
                            ErrorInfo(e, UserAction.DATABASE_IMPORT_EXPORT, "Importing preferences")
                        )
                    }
                    finishImport(uri)
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = {
                    dismiss()
                    finishImport(uri)
                }
            )
        }.apply { setCancelable(false) }.show()
    }

    /**
     * Media tunneling disabled automatically on the device of the backup must not stay
     * disabled here: reset it and let this device decide.
     */
    private fun cleanImport(context: Context, prefs: SharedPreferences) {
        val tunnelingKey = context.getString(R.string.disable_media_tunneling_key)
        val automaticKey = context.getString(R.string.disabled_media_tunneling_automatically_key)
        if (prefs.getInt(automaticKey, -1) == 1 && prefs.getBoolean(tunnelingKey, false)) {
            prefs.edit {
                putInt(automaticKey, -1)
                putBoolean(tunnelingKey, false)
            }
            NewPipeSettings.setMediaTunneling(context)
        }
    }

    private fun finishImport(uri: Uri) {
        saveLastDataUri(uri) // only on success
        // restart the app to load the new database
        NavigationHelper.restartApp(requireActivity())
    }

    private fun lastDataUri(): Uri? = prefs.getString(getString(R.string.import_export_data_path), null)
        ?.takeIf { it.isNotBlank() }
        ?.let { Uri.parse(it) }

    private fun saveLastDataUri(uri: Uri) {
        prefs.edit { putString(getString(R.string.import_export_data_path), uri.toString()) }
    }

    private fun confirm(message: Int, onConfirm: () -> Unit) {
        YpDialogs.card(requireContext()) { dismiss ->
            YpDialogCard(
                title = stringResource(R.string.settings_category_backup_restore_title),
                message = stringResource(message),
                confirmLabel = stringResource(R.string.ok),
                onConfirm = {
                    dismiss()
                    onConfirm()
                },
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = dismiss
            )
        }.show()
    }

    private fun showError(e: Throwable, request: String) {
        ErrorUtil.showSnackbar(this, ErrorInfo(e, UserAction.DATABASE_IMPORT_EXPORT, request))
    }

    private companion object {
        const val TAG = "BackupRestoreSettings"
        const val ZIP_MIME_TYPE = "application/zip"
    }
}
