package com.ivandsky.puzzlebox.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URLEncoder

/**
 * Raw reads and writes of the cloud-sync bookkeeping: a cursor per (uid, kind), the last signed-in uid, a
 * restore-shown flag per uid and a daily write counter per uid. It takes a DataStore of its own file so
 * backup rules can exclude the bookkeeping alone: a restored cursor would make a new device skip documents
 * it never downloaded. A uid is percent-encoded inside every key, so no uid can address another uid's keys.
 * It reads no clock; the caller supplies the day.
 */
class SyncStateDataSource(
    private val dataStore: DataStore<Preferences>,
) {

    val lastUid: Flow<String?> = dataStore.data.map { it[KEY_LAST_UID] }

    suspend fun cursor(uid: String, kind: String): Long? = dataStore.data.first()[cursorKey(uid, kind)]

    suspend fun setCursor(uid: String, kind: String, cursorMs: Long) {
        dataStore.edit { it[cursorKey(uid, kind)] = cursorMs }
    }

    suspend fun setLastUid(uid: String) {
        dataStore.edit { it[KEY_LAST_UID] = uid }
    }

    suspend fun restoreShown(uid: String): Boolean = dataStore.data.first()[restoreKey(uid)] ?: false

    suspend fun markRestoreShown(uid: String) {
        dataStore.edit { it[restoreKey(uid)] = true }
    }

    /**
     * Adds [n] to [uid]'s counter for [day] and returns that day's total. A stored day other than [day]
     * is dropped first, so the first count of a new day starts again from [n]. Read and write are one edit.
     */
    suspend fun countWrites(uid: String, day: String, n: Int): Int {
        val dayKey = writesDayKey(uid)
        val countKey = writesCountKey(uid)
        var total = 0
        dataStore.edit { preferences ->
            val sameDay = preferences[dayKey] == day
            total = (if (sameDay) preferences[countKey] ?: 0 else 0) + n
            preferences[dayKey] = day
            preferences[countKey] = total
        }
        return total
    }

    /** Drops [uid]'s cursors and restore flag. The last uid and the write counter stay. */
    suspend fun forget(uid: String) {
        val cursorPrefix = cursorPrefix(uid)
        val restoreKey = restoreKey(uid)
        dataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { it.name.startsWith(cursorPrefix) || it.name == restoreKey.name }
                .forEach { preferences.remove(it) }
        }
    }

    private companion object {
        val KEY_LAST_UID = stringPreferencesKey("last_uid")

        // URLEncoder never emits '/', so a separator '/' keeps (uid, kind) pairs unambiguous.
        fun encoded(uid: String): String = URLEncoder.encode(uid, "UTF-8")

        fun cursorPrefix(uid: String) = "cursor/${encoded(uid)}/"
        fun cursorKey(uid: String, kind: String) = longPreferencesKey(cursorPrefix(uid) + kind)
        fun restoreKey(uid: String) = booleanPreferencesKey("restore_shown/${encoded(uid)}")
        fun writesDayKey(uid: String) = stringPreferencesKey("writes_day/${encoded(uid)}")
        fun writesCountKey(uid: String) = intPreferencesKey("writes_count/${encoded(uid)}")
    }
}
