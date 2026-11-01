package com.ivandsky.puzzlebox.app.debug

import android.content.Context

/**
 * Expansion-state persistence for the debug Showkase browser (spec A1). SharedPreferences
 * keeps the debug source set dependency-free — :app doesn't declare DataStore — and this
 * class never leaves the debug variant, so release APK cost stays zero.
 */
internal class ShowkaseBrowserPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    /**
     * Returns a COPY: getStringSet hands back the internally-stored set, which must
     * never be mutated in place (silent write-loss).
     */
    fun expandedGroups(): Set<String> =
        prefs.getStringSet(KEY_EXPANDED_GROUPS, emptySet())?.toSet().orEmpty()

    fun setExpandedGroups(groups: Set<String>) {
        prefs.edit().putStringSet(KEY_EXPANDED_GROUPS, groups).apply()
    }

    // internal, not private: the screenshot suite asserts the write-through path of
    // the bulk expand/collapse action against this exact file/keys (ticket 06).
    internal companion object {
        internal const val PREFERENCES_FILE = "showkase_browser"
        internal const val KEY_EXPANDED_GROUPS = "expanded_groups"
    }
}
