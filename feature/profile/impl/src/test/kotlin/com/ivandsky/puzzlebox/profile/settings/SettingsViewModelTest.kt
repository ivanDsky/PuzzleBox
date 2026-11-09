package com.ivandsky.puzzlebox.profile.settings

import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Settings ViewModel: a theme pick is persisted and the state follows it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `choosing dark persists the theme`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(preferences, AppBuild(versionName = "1.4.0", checksForUpdates = false))
        // The state is observed only while subscribed (the route's lifecycle collector).
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.SYSTEM, "1.4.0"), vm.uiState.value)

        vm.onSetThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
        assertEquals(SettingsUiState(ThemeMode.DARK, "1.4.0"), vm.uiState.value)
    }
}
