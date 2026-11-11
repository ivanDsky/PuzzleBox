package com.ivandsky.puzzlebox.profile.settings

import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings ViewModel: a theme pick is persisted and the LinkedIn section follows the link. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val appBuild = AppBuild(versionName = "1.4.0", checksForUpdates = false)

    /** The state is observed only while subscribed (the route's lifecycle collector). */
    private suspend fun TestScope.observed(link: LinkedInLink?): SettingsViewModel {
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), appBuild, link)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `choosing dark persists the theme`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(preferences, appBuild, link = null)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.SYSTEM, "1.4.0"), vm.uiState.value)

        vm.onSetThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
        assertEquals(SettingsUiState(ThemeMode.DARK, "1.4.0"), vm.uiState.value)
    }

    @Test
    fun `the section follows the link`() = runTest(dispatcher) {
        val link = FakeLinkedInLink()
        val vm = observed(link)
        assertEquals(LinkedInSectionState.NotConnected, vm.uiState.value.linkedIn)

        link.connect("me", consentAtMs = 1L)
        advanceUntilIdle()
        assertEquals(LinkedInSectionState.Connected, vm.uiState.value.linkedIn)

        link.pause(LinkedInRead.Rejected.Kind.Challenge)
        advanceUntilIdle()
        val challenge = vm.uiState.value.linkedIn as LinkedInSectionState.Paused
        assertTrue(challenge.reason.contains("check"))
        assertEquals(false, challenge.needsReconnect)

        link.pause(LinkedInRead.Rejected.Kind.SessionExpired)
        advanceUntilIdle()
        val expired = vm.uiState.value.linkedIn as LinkedInSectionState.Paused
        assertEquals(true, expired.needsReconnect)
    }

    @Test
    fun `resume from paused connects and disconnect clears`() = runTest(dispatcher) {
        val link = FakeLinkedInLink()
        val vm = observed(link)
        link.connect("me", consentAtMs = 1L)
        link.pause(LinkedInRead.Rejected.Kind.RateLimited)
        advanceUntilIdle()

        vm.onResumeLinkedIn()
        advanceUntilIdle()
        assertEquals(LinkedInSectionState.Connected, vm.uiState.value.linkedIn)

        vm.onDisconnectLinkedIn()
        advanceUntilIdle()
        assertEquals(LinkedInSectionState.NotConnected, vm.uiState.value.linkedIn)

        link.connect("me", consentAtMs = 2L)
        link.pause(LinkedInRead.Rejected.Kind.Unexpected)
        advanceUntilIdle()
        vm.onDisconnectLinkedIn()
        advanceUntilIdle()
        assertEquals(LinkedInSectionState.NotConnected, vm.uiState.value.linkedIn)
    }
}
