package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The replay "Show errors" switch, one global preference shared by the replay and compare pages: on until [load] reads
 * the stored value (the page calls it before leaving Loading), and every [set] writes the preference.
 */
internal class ReplayShowErrors(private val preferences: UserPreferencesRepository) {

    private val _value = MutableStateFlow(true)
    val value: StateFlow<Boolean> = _value

    suspend fun load() {
        _value.value = preferences.showReplayErrors.first()
    }

    fun set(on: Boolean, scope: CoroutineScope) {
        _value.value = on
        scope.launch { preferences.setShowReplayErrors(on) }
    }
}
