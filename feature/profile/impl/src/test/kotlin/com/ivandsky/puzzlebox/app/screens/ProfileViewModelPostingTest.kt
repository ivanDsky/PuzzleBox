package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.profile.accounts.LinkedInRowState
import com.ivandsky.puzzlebox.profile.domain.linkedin.ObserveLinkedInAccount
import com.ivandsky.puzzlebox.profile.domain.linkedin.UpdateLinkedInPosting
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.cloud.FakeOversizedDrafts
import com.ivandsky.puzzlebox.testing.cloud.FakeSavedBoardsCloud
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccount
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Profile ViewModel's posting switch: backed by the link's own flow. */
class ProfileViewModelPostingTest {

    private val dispatcher = StandardTestDispatcher()
    private val registry = GameRegistry(listOf(TestGames.plugin(GameId("zip"))))
    private val clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private object UnavailableGoogleIdTokenProvider : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken =
            GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.OTHER)
    }

    private fun TestScope.viewModel(link: FakeLinkedInLink): ProfileViewModel {
        val completions = FakeCompletionRepository()
        val vm = ProfileViewModel(
            identityService = FakeIdentityService(initial = Identity("u1", "Ann", null)),
            idTokenProvider = UnavailableGoogleIdTokenProvider,
            observeGameStats = ObserveGameStats(
                registry,
                ObserveGameHistory(registry, FakeDailyRepository(), completions),
                ComputeStreak(GetPuzzleDay(registry, clock)),
            ),
            link = link,
            observeAccount = ObserveLinkedInAccount(FakeLinkedInAccount(), link, completions, GetPuzzleDay(registry, clock), registry),
            updatePosting = UpdateLinkedInPosting(link, clock = { 5_000L }),
            cloudSync = FakeCloudSync(),
            oversizedDrafts = FakeOversizedDrafts(),
            savedBoardsCloud = FakeSavedBoardsCloud(),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun ProfileViewModel.connected() = uiState.value.linkedIn as LinkedInRowState.Connected

    @Test
    fun `connected the switch is offered and off by default`() = runTest(dispatcher) {
        val vm = viewModel(FakeLinkedInLink(LinkState.Connected("me")))

        assertFalse(vm.connected().summary.posting)
    }

    @Test
    fun `turning it on and off follows the link`() = runTest(dispatcher) {
        val link = FakeLinkedInLink(LinkState.Connected("me"))
        val vm = viewModel(link)

        vm.onSetLinkedInPosting(true)
        advanceUntilIdle()
        assertTrue(vm.connected().summary.posting)
        assertEquals(listOf<Long?>(5_000L), link.postingChanges)

        vm.onSetLinkedInPosting(false)
        advanceUntilIdle()
        assertFalse(vm.connected().summary.posting)
    }

    @Test
    fun `a paused link still offers the switch and lets it change`() = runTest(dispatcher) {
        val link = FakeLinkedInLink(LinkState.Paused("me", Kind.Challenge))
        val vm = viewModel(link)

        vm.onSetLinkedInPosting(true)
        advanceUntilIdle()

        val row = vm.uiState.value.linkedIn as LinkedInRowState.Paused
        assertTrue(row.summary.posting)
    }

    @Test
    fun `disconnect turns posting off and a reconnect starts off`() = runTest(dispatcher) {
        val link = FakeLinkedInLink(LinkState.Connected("me"))
        val vm = viewModel(link)
        vm.onSetLinkedInPosting(true)
        advanceUntilIdle()

        vm.onDisconnectLinkedIn()
        advanceUntilIdle()
        assertEquals(LinkedInRowState.NotConnected, vm.uiState.value.linkedIn)
        link.connect("me", consentAtMs = 9L)
        advanceUntilIdle()

        assertFalse(vm.connected().summary.posting)
    }
}
