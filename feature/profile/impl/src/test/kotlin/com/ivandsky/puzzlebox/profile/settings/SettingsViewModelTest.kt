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

/** Settings ViewModel: a theme pick and the Games switches are persisted. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val appBuild = AppBuild(versionName = "1.4.0", checksForUpdates = false)

    @Test
    fun `choosing dark persists the theme`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(preferences, FakeGameOptions(), appBuild)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.SYSTEM, "1.4.0"), vm.uiState.value)

        vm.onSetThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
        assertEquals(SettingsUiState(ThemeMode.DARK, "1.4.0"), vm.uiState.value)
    }

    @Test
    fun `each games switch writes its own stored value and leaves the others`() = runTest(dispatcher) {
        val games = FakeGameOptions()
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), games, appBuild)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onSetQueensAutoCross(false)
        advanceUntilIdle()
        assertEquals(false, games.queensAutoCross.value)
        assertEquals(GamesSectionState(queensAutoCross = false), vm.uiState.value.games)

        vm.onSetSudokuPeerHighlight(false)
        advanceUntilIdle()
        assertEquals(GamesSectionState(queensAutoCross = false, sudokuPeerHighlight = false), vm.uiState.value.games)

        vm.onSetSudokuSameDigitHighlight(false)
        advanceUntilIdle()
        assertEquals(GamesSectionState(false, false, false), vm.uiState.value.games)
    }

    @Test
    fun `the games state follows the store when it changes elsewhere`() = runTest(dispatcher) {
        val games = FakeGameOptions()
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), games, appBuild)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        games.sudokuSameDigitHighlight.value = false
        advanceUntilIdle()
        assertEquals(GamesSectionState(sudokuSameDigitHighlight = false), vm.uiState.value.games)

        games.sudokuSameDigitHighlight.value = true
        advanceUntilIdle()
        assertEquals(GamesSectionState(), vm.uiState.value.games)
    }
}
