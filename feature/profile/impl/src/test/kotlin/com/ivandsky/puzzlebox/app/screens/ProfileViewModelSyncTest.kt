package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.data.cloud.PauseReason
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.app.screens.ProfileUiState.GoogleSync
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
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

/** Profile ViewModel cloud-sync mapping (A32): the engine's state as the Google row's line, Sync now, Delete my cloud data. */
class ProfileViewModelSyncTest {

    private val dispatcher = StandardTestDispatcher()
    private val registry = GameRegistry(listOf(TestGames.plugin(GameId("zip"))))
    private val clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private object UnavailableGoogleIdTokenProvider : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.OTHER)
    }

    private fun TestScope.viewModel(cloud: FakeCloudSync?): ProfileViewModel {
        val history = ObserveGameHistory(registry, FakeDailyRepository(), FakeCompletionRepository())
        val vm = ProfileViewModel(
            identityService = FakeIdentityService(initial = Identity("u1", "Ann", null)),
            idTokenProvider = UnavailableGoogleIdTokenProvider,
            observeGameStats = ObserveGameStats(registry, history, ComputeStreak(GetPuzzleDay(registry, clock))),
            cloudSync = cloud,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun ProfileViewModel.sync(): GoogleSync? = uiState.value.sync

    @Test
    fun `with cloud sync off nothing sync is in the state and the actions do nothing`() = runTest(dispatcher) {
        val vm = viewModel(null)

        vm.onSyncNow()
        vm.onDeleteCloudData()
        advanceUntilIdle()

        assertNull(vm.sync())
    }

    @Test
    fun `signed out of cloud sync carries no sync line`() = runTest(dispatcher) {
        assertNull(viewModel(FakeCloudSync(CloudSyncState.SignedOut)).sync())
    }

    @Test
    fun `the line follows the engine state`() = runTest(dispatcher) {
        val cloud = FakeCloudSync(CloudSyncState.Synced(atMs = 5_000L))
        val vm = viewModel(cloud)
        assertEquals(GoogleSync(GoogleSync.Status.Synced(5_000L)), vm.sync())

        cloud.state.value = CloudSyncState.Syncing(firstRestore = false)
        advanceUntilIdle()
        assertEquals(GoogleSync(GoogleSync.Status.Syncing), vm.sync())

        cloud.state.value = CloudSyncState.Syncing(firstRestore = true)
        advanceUntilIdle()
        assertEquals(GoogleSync(GoogleSync.Status.Syncing), vm.sync())

        cloud.state.value = CloudSyncState.Offline(lastAtMs = null)
        advanceUntilIdle()
        assertEquals(GoogleSync(GoogleSync.Status.Offline), vm.sync())

        cloud.state.value = CloudSyncState.Offline(lastAtMs = 5_000L)
        advanceUntilIdle()
        assertEquals(GoogleSync(GoogleSync.Status.Offline), vm.sync())

        PauseReason.entries.forEach { reason ->
            cloud.state.value = CloudSyncState.Paused(reason, lastAtMs = 5_000L)
            advanceUntilIdle()
            assertEquals(GoogleSync(GoogleSync.Status.Paused), vm.sync())
        }

        cloud.state.value = CloudSyncState.SignedOut
        advanceUntilIdle()
        assertNull(vm.sync())
    }

    @Test
    fun `sync now asks the engine for a pass`() = runTest(dispatcher) {
        val cloud = FakeCloudSync(CloudSyncState.Offline(lastAtMs = null))
        val vm = viewModel(cloud)

        vm.onSyncNow()
        advanceUntilIdle()

        assertEquals(1, cloud.syncNowCalls)
    }

    @Test
    fun `delete my cloud data erases and leaves no message when it works`() = runTest(dispatcher) {
        val cloud = FakeCloudSync(CloudSyncState.Synced(1L))
        val vm = viewModel(cloud)

        vm.onDeleteCloudData()
        advanceUntilIdle()

        assertEquals(1, cloud.eraseCloudCalls)
        assertEquals(GoogleSync(GoogleSync.Status.Synced(1L)), vm.sync())
    }

    @Test
    fun `an offline erase says so and stays retryable`() = runTest(dispatcher) {
        val cloud = FakeCloudSync(CloudSyncState.Synced(1L)).apply { eraseResult = CloudResult.Offline }
        val vm = viewModel(cloud)

        vm.onDeleteCloudData()
        advanceUntilIdle()

        assertEquals(
            GoogleSync(GoogleSync.Status.Synced(1L), eraseMessage = "You're offline. Try again when you're connected."),
            vm.sync(),
        )

        cloud.eraseResult = CloudResult.Ok(Unit)
        vm.onDeleteCloudData()
        advanceUntilIdle()

        assertEquals(2, cloud.eraseCloudCalls)
        assertNull(vm.sync()?.eraseMessage)
    }

    @Test
    fun `a refused erase gets the generic failure line`() = runTest(dispatcher) {
        listOf(CloudResult.Denied, CloudResult.Exhausted, CloudResult.WrongAccount).forEach { result ->
            val cloud = FakeCloudSync(CloudSyncState.Synced(1L)).apply { eraseResult = result }
            val vm = viewModel(cloud)

            vm.onDeleteCloudData()
            advanceUntilIdle()

            assertEquals("Couldn't delete your cloud data. Try again later.", vm.sync()?.eraseMessage)
        }
    }

    @Test
    fun `sync now clears an old erase message`() = runTest(dispatcher) {
        val cloud = FakeCloudSync(CloudSyncState.Synced(1L)).apply { eraseResult = CloudResult.Offline }
        val vm = viewModel(cloud)
        vm.onDeleteCloudData()
        advanceUntilIdle()

        vm.onSyncNow()
        advanceUntilIdle()

        assertNull(vm.sync()?.eraseMessage)
    }
}
