package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The scope the hub opens on (F1): the stored default, read before the first frame, and followed after. */
@OptIn(ExperimentalCoroutinesApi::class)
class RanksLandingTest {

    private fun scope() = CoroutineScope(UnconfinedTestDispatcher() + Job())

    @Test
    fun `a stored Global is read off the calling thread, Friends until it loads`() = runTest {
        // A scope that has not run yet: nothing blocks to read the store, so the value is the placeholder.
        val scope = CoroutineScope(StandardTestDispatcher() + Job())

        val landing = RanksLanding(FakeUserPreferencesRepository(ranksFriendsDefault = false), scope)
        assertEquals(RankScope.Friends, landing.scope.value)

        scope.launch { }.also { testScheduler.advanceUntilIdle() }
        assertEquals(RankScope.Global, landing.stored.first())
        scope.cancel()
    }

    @Test
    fun `the default is Friends`() = runTest {
        val scope = scope()

        val landing = RanksLanding(FakeUserPreferencesRepository(), scope)

        assertEquals(RankScope.Friends, landing.scope.value)
        scope.cancel()
    }

    @Test
    fun `it follows a change of the setting`() = runTest {
        val scope = scope()
        val preferences = FakeUserPreferencesRepository(ranksFriendsDefault = true)
        val landing = RanksLanding(preferences, scope)

        preferences.setRanksFriendsDefault(false)
        assertEquals(RankScope.Global, landing.scope.value)

        preferences.setRanksFriendsDefault(true)
        assertEquals(RankScope.Friends, landing.scope.value)
        scope.cancel()
    }
}
