package com.ivandsky.puzzlebox.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ivandsky.puzzlebox.model.ReminderSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Raw reads and writes of the daily-reminder choice: an enabled flag and the time of day as minutes
 * since midnight. It takes a DataStore of its own file, so nothing creates the file before the first
 * write and the shared preferences stay untouched. An absent key reads as the [ReminderSettings]
 * default, and a stored minute outside 0..1439 reads as the default time.
 */
class ReminderPrefsDataSource(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<ReminderSettings> = dataStore.data.map { preferences ->
        val default = ReminderSettings()
        val minutes = preferences[KEY_TIME_MINUTES]
        ReminderSettings(
            enabled = preferences[KEY_ENABLED] ?: default.enabled,
            time = if (minutes != null && minutes in 0 until MINUTES_PER_DAY) {
                LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
            } else {
                default.time
            },
        )
    }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    /** Stores [time] as minutes since midnight; seconds and below are dropped. */
    suspend fun setTime(time: LocalTime) {
        dataStore.edit { it[KEY_TIME_MINUTES] = time.hour * MINUTES_PER_HOUR + time.minute }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_TIME_MINUTES = intPreferencesKey("time_minutes")
    }
}
