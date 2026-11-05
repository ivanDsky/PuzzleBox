package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Handwritten fake for app and feature tests: in-memory preferences, no DataStore file. */
class FakeUserPreferencesRepository(initial: ThemeMode = ThemeMode.SYSTEM) : UserPreferencesRepository {

    private val state = MutableStateFlow(initial)

    /** Every mode written through [setThemeMode], oldest first. */
    val setCalls = mutableListOf<ThemeMode>()

    override val themeMode: StateFlow<ThemeMode> = state.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        setCalls += mode
        state.value = mode
    }
}
