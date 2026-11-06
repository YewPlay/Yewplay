/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.engine

import android.content.Context
import androidx.preference.PreferenceManager

/** SharedPreferences-backed [EnginePrefs] used by the app. */
class SharedPreferencesEnginePrefs(context: Context) : EnginePrefs {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}
