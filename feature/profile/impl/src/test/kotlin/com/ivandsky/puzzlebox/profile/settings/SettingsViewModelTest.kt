package com.ivandsky.puzzlebox.profile.settings

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Settings ViewModel: a theme pick and the Games switches are persisted. */
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val appBuild = AppBuild(versionName = "1.4.0", checksForUpdates = false)
    private val context: Context = org.mockito.Mockito.mock(Context::class.java)
    private val identity = FakeIdentityService()
    private var token: GoogleIdToken = GoogleIdToken.Available("id-token")
    private var tokens: GoogleIdTokenProvider = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = token
    }

    @Test
    fun `choosing dark persists the theme`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(preferences, FakeGameOptions(), appBuild, identity, tokens)
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
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), games, appBuild, identity, tokens)
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
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), games, appBuild, identity, tokens)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        games.sudokuSameDigitHighlight.value = false
        advanceUntilIdle()
        assertEquals(GamesSectionState(sudokuSameDigitHighlight = false), vm.uiState.value.games)

        games.sudokuSameDigitHighlight.value = true
        advanceUntilIdle()
        assertEquals(GamesSectionState(), vm.uiState.value.games)
    }

    private fun slotOf(repository: FakeVisibilityRepository) = VisibilitySlot(repository.ownTier, repository::setOwnTier)

    @Test
    fun `picking a tier calls setOwnTier and the section shows the stored tier`() = runTest(dispatcher) {
        val repository = FakeVisibilityRepository(OwnTier.Unset)
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), FakeGameOptions(), appBuild, identity, tokens, slotOf(repository))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(OwnTier.Unset, vm.uiState.value.visibility)

        vm.onSelectVisibility(VisibilityTier.Duel)
        advanceUntilIdle()

        assertEquals(listOf(VisibilityTier.Duel), repository.setCalls)
        assertEquals(OwnTier.Chosen(VisibilityTier.Duel), vm.uiState.value.visibility)
    }

    @Test
    fun `a signed-out pick writes nothing and leaves the tier unknown`() = runTest(dispatcher) {
        val repository = FakeVisibilityRepository().apply { signedIn = false }
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), FakeGameOptions(), appBuild, identity, tokens, slotOf(repository))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // A failure thrown into the ViewModel's scope would fail this test when runTest ends.
        vm.onSelectVisibility(VisibilityTier.Duel)
        advanceUntilIdle()

        assertEquals(emptyList<VisibilityTier>(), repository.setCalls)
        assertEquals(OwnTier.Unknown, vm.uiState.value.visibility)
    }

    @Test
    fun `the section is absent without a slot and a pick touches nothing`() = runTest(dispatcher) {
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), FakeGameOptions(), appBuild, identity, tokens)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onSelectVisibility(VisibilityTier.Hidden)
        advanceUntilIdle()

        assertNull(vm.uiState.value.visibility)
    }

    @Test
    fun `the ranks default follows the stored scope and a pick writes it`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(preferences, FakeGameOptions(), appBuild, identity, tokens, slotOf(FakeVisibilityRepository(OwnTier.Unset)))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.ranksFriendsDefault)

        vm.onSetRanksFriendsDefault(false)
        advanceUntilIdle()

        assertEquals(false, preferences.ranksFriendsDefault.first())
        assertEquals(false, vm.uiState.value.ranksFriendsDefault)
    }

    @Test
    fun `vibration follows the stored switch and a flip writes it`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(preferences, FakeGameOptions(), appBuild, identity, tokens)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.vibration)

        vm.onSetVibration(false)
        advanceUntilIdle()

        assertEquals(false, preferences.vibration.first())
        assertEquals(false, vm.uiState.value.vibration)
    }

    @Test
    fun `without the slot a ranks pick writes nothing`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(preferences, FakeGameOptions(), appBuild, identity, tokens)

        vm.onSetRanksFriendsDefault(false)
        advanceUntilIdle()

        assertEquals(true, preferences.ranksFriendsDefault.first())
    }

    @Test
    fun `the play section shows the stored time-left switch and a flip writes it`() = runTest(dispatcher) {
        val preferences = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(preferences, FakeGameOptions(), appBuild, identity, tokens)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.showSelfDuelTimeLeft)

        vm.onSetShowSelfDuelTimeLeft(false)
        advanceUntilIdle()

        assertEquals(false, preferences.showSelfDuelTimeLeft.first())
        assertEquals(false, vm.uiState.value.showSelfDuelTimeLeft)
    }

    private fun TestScope.signedOutVm(): SettingsViewModel {
        val vm = SettingsViewModel(
            FakeUserPreferencesRepository(), FakeGameOptions(), appBuild, identity, tokens,
            slotOf(FakeVisibilityRepository(OwnTier.Unknown)),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `sign-in from Visibility exchanges the Google token with the identity service`() = runTest(dispatcher) {
        val vm = signedOutVm()

        vm.onSignIn(context)
        advanceUntilIdle()

        assertEquals(listOf("id-token"), identity.signInCalls)
        assertNull(vm.uiState.value.signInMessage)
    }

    @Test
    fun `a second tap while sign-in runs starts no second credential flow`() = runTest(dispatcher) {
        var calls = 0
        tokens = object : GoogleIdTokenProvider {
            override suspend fun idToken(activityContext: Context): GoogleIdToken {
                calls++
                return token
            }
        }
        val vm = signedOutVm()

        vm.onSignIn(context)
        vm.onSignIn(context)
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `the state says signed in once the identity service has an account`() = runTest(dispatcher) {
        val vm = signedOutVm()
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.signedIn)

        vm.onSignIn(context)
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.signedIn)
    }

    @Test
    fun `a failed sign-in from Visibility is said and a backed-out sheet is not`() = runTest(dispatcher) {
        val vm = signedOutVm()

        identity.nextSignInResult = AuthResult.Err(AuthError.Unavailable("offline"))
        vm.onSignIn(context)
        advanceUntilIdle()
        assertEquals("Sign-in is unavailable right now. Check your connection.", vm.uiState.value.signInMessage)

        token = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
        vm.onSignIn(context)
        advanceUntilIdle()
        assertNull(vm.uiState.value.signInMessage)
    }
}
