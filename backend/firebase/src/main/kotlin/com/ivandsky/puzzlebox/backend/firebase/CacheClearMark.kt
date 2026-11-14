package com.ivandsky.puzzlebox.backend.firebase

import android.content.Context

/**
 * The pending-clear mark: sign-out writes it and the next cold start reads it before any Firestore use, because
 * `clearPersistence()` only works while the Firestore instance has not started. Survives the process, so a sign-out
 * followed by a kill still clears on the next start.
 */
internal interface CacheClearMark {
    fun mark()

    fun isPending(): Boolean

    fun drop()
}

/** [CacheClearMark] in a small private preferences file; writes commit synchronously, so a kill right after sign-out keeps the mark. */
internal class PreferencesCacheClearMark(context: Context) : CacheClearMark {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun mark() {
        prefs.edit().putBoolean(KEY, true).commit()
    }

    override fun isPending(): Boolean = prefs.getBoolean(KEY, false)

    override fun drop() {
        prefs.edit().remove(KEY).commit()
    }

    private companion object {
        const val FILE = "firestore_cache_clear"
        const val KEY = "pending"
    }
}
