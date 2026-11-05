package com.ivandsky.puzzlebox.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Sole owner of the `kept_sessions` Preferences DataStore file's keys (spec A3). Each game
 * holds three keys, `kept_<gameId>_{blob,day,elapsed}`: the SessionBlob envelope bytes, the
 * cadence day as an ISO date, and the displayed elapsed ms. Writes set or clear all three in
 * one edit, so an entry is never half-written; reads skip any entry with a missing or
 * unparseable part rather than guessing it.
 */
class KeptSessionsDataSource(
    private val dataStore: DataStore<Preferences>,
) {

    /** One game's stored entry, exactly as the file holds it. */
    class Entry(val game: GameId, val day: LocalDate, val elapsedMs: Long, val envelope: ByteArray)

    /** Every complete entry in the file, re-emitted on each change. */
    val entries: Flow<List<Entry>> = dataStore.data.map { preferences ->
        preferences.asMap().keys
            .mapNotNull { key -> gameOf(key.name) }
            .mapNotNull { game -> preferences.entryFor(game) }
    }

    /** Stores [entry], replacing whatever that game held. */
    suspend fun put(entry: Entry) {
        val keys = Keys(entry.game)
        dataStore.edit { preferences ->
            preferences[keys.blob] = entry.envelope
            preferences[keys.day] = entry.day.toString()
            preferences[keys.elapsed] = entry.elapsedMs
        }
    }

    /** Clears [game]'s entry; a game with nothing stored is a no-op. */
    suspend fun remove(game: GameId) {
        val keys = Keys(game)
        dataStore.edit { preferences ->
            preferences.remove(keys.blob)
            preferences.remove(keys.day)
            preferences.remove(keys.elapsed)
        }
    }

    private fun Preferences.entryFor(game: GameId): Entry? {
        val keys = Keys(game)
        val blob = this[keys.blob] ?: return null
        val day = this[keys.day]?.let(::parseDay) ?: return null
        val elapsed = this[keys.elapsed] ?: return null
        return Entry(game, day, elapsed, blob)
    }

    private class Keys(game: GameId) {
        val blob = byteArrayPreferencesKey("$PREFIX${game.value}$BLOB")
        val day = stringPreferencesKey("$PREFIX${game.value}$DAY")
        val elapsed = longPreferencesKey("$PREFIX${game.value}$ELAPSED")
    }

    private companion object {
        const val PREFIX = "kept_"
        const val BLOB = "_blob"
        const val DAY = "_day"
        const val ELAPSED = "_elapsed"

        /** The game a `kept_<gameId>_blob` key belongs to; null for every other key. */
        fun gameOf(keyName: String): GameId? {
            if (!keyName.startsWith(PREFIX) || !keyName.endsWith(BLOB)) return null
            val id = keyName.substring(PREFIX.length, keyName.length - BLOB.length)
            return if (id.isEmpty()) null else GameId(id)
        }

        fun parseDay(stored: String): LocalDate? =
            try {
                LocalDate.parse(stored)
            } catch (_: DateTimeParseException) {
                null
            }
    }
}
