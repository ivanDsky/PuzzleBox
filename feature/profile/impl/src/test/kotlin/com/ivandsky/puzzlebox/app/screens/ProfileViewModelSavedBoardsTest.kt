package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import com.ivandsky.puzzlebox.app.screens.ProfileUiState.LinkedInBoards
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.cloud.FakeOversizedDrafts
import com.ivandsky.puzzlebox.testing.cloud.FakeSavedBoardsCloud
import com.ivandsky.puzzlebox.testing.game.TestGames
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Profile ViewModel's LinkedIn-row side of cloud sync (A27, A35): the saved-boards flags and Delete saved boards. */
class ProfileViewModelSavedBoardsTest {

    private val dispatcher = StandardTestDispatcher()
    private val registry = GameRegistry(listOf(TestGames.plugin(GameId("zip"))))
    private val clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private object UnavailableGoogleIdTokenProvider : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.OTHER)
    }

    private fun TestScope.viewModel(cloud: FakeCloudSync, boards: FakeSavedBoardsCloud?): ProfileViewModel {
        val history = ObserveGameHistory(registry, FakeDailyRepository(), FakeCompletionRepository())
        val vm = ProfileViewModel(
            identityService = FakeIdentityService(initial = Identity("u1", "Ann", null)),
            idTokenProvider = UnavailableGoogleIdTokenProvider,
            observeGameStats = ObserveGameStats(registry, history, ComputeStreak(GetPuzzleDay(registry, clock))),
            cloudSync = cloud,
            oversizedDrafts = FakeOversizedDrafts(),
            savedBoardsCloud = boards,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun ProfileViewModel.boards(): LinkedInBoards = uiState.value.linkedInBoards

    @Test
    fun `without the saved-boards binding the LinkedIn row offers no delete and the action does nothing`() = runTest(dispatcher) {
        val vm = viewModel(FakeCloudSync(CloudSyncState.SignedOut), null)

        vm.onDeleteSavedBoards()
        advanceUntilIdle()

        assertEquals(LinkedInBoards(canDelete = false), vm.boards())
    }

    @Test
    fun `cloud boards offer delete`() = runTest(dispatcher) {
        val vm = viewModel(FakeCloudSync(CloudSyncState.Synced(1L)), FakeSavedBoardsCloud())

        assertEquals(LinkedInBoards(canDelete = true), vm.boards())
    }

    @Test
    fun `delete saved boards deletes everywhere and leaves no message when it works`() = runTest(dispatcher) {
        val cloud = FakeSavedBoardsCloud()
        val vm = viewModel(FakeCloudSync(CloudSyncState.Synced(1L)), cloud)

        vm.onDeleteSavedBoards()
        advanceUntilIdle()

        assertEquals(1, cloud.deleteEverywhereCalls)
        assertEquals(LinkedInBoards(canDelete = true), vm.boards())
    }

    @Test
    fun `an offline delete says so and stays retryable`() = runTest(dispatcher) {
        val cloud = FakeSavedBoardsCloud().apply { result = CloudResult.Offline }
        val vm = viewModel(FakeCloudSync(CloudSyncState.Synced(1L)), cloud)

        vm.onDeleteSavedBoards()
        advanceUntilIdle()

        assertEquals("You're offline. Try again when you're connected.", vm.boards().deleteMessage)

        cloud.result = CloudResult.Ok(Unit)
        vm.onDeleteSavedBoards()
        advanceUntilIdle()

        assertEquals(2, cloud.deleteEverywhereCalls)
        assertNull(vm.boards().deleteMessage)
    }

    @Test
    fun `a refused delete gets the generic failure line`() = runTest(dispatcher) {
        val cloud = FakeSavedBoardsCloud().apply { result = CloudResult.Denied }
        val vm = viewModel(FakeCloudSync(), cloud)

        vm.onDeleteSavedBoards()
        advanceUntilIdle()

        assertEquals(LinkedInBoards(canDelete = true, deleteMessage = "Couldn't delete your saved boards. Try again later."), vm.boards())
    }
}
