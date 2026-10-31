package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Handwritten fake for feature tests: in-memory mode, recorded writes. */
class FakeUserPreferencesRepository(
    initial: ThemeMode = ThemeMode.SYSTEM,
) : UserPreferencesRepository {

    private val modes = MutableStateFlow(initial)

    val setCalls = mutableListOf<ThemeMode>()

    override val themeMode: Flow<ThemeMode> = modes.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        setCalls += mode
        modes.value = mode
    }
}
