package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The scope and games the hub opens on (F1): the stored defaults, read before the first frame, and followed after. */
@OptIn(ExperimentalCoroutinesApi::class)
class RanksLandingTest {

    private fun scope() = CoroutineScope(UnconfinedTestDispatcher() + Job())

    @Test
    fun `the default with no friends is Global`() = runTest {
        val scope = scope()

        val landing = RanksLanding(FakeUserPreferencesRepository(), flowOf(false), gamesFilter = false, scope)

        assertEquals(RankScope.Global, landing.scope.value)
        scope.cancel()
    }

    @Test
    fun `the default with a friend is Friends`() = runTest {
        val scope = scope()

        val landing = RanksLanding(FakeUserPreferencesRepository(), flowOf(true), gamesFilter = false, scope)

        assertEquals(RankScope.Friends, landing.scope.value)
        scope.cancel()
    }

    @Test
    fun `it follows a change of the setting and of the friends`() = runTest {
        val scope = scope()
        val preferences = FakeUserPreferencesRepository(ranksFriendsDefault = true)
        val friends = MutableStateFlow(true)
        val landing = RanksLanding(preferences, friends, gamesFilter = false, scope)

        preferences.setRanksFriendsDefault(false)
        assertEquals(RankScope.Global, landing.scope.value)

        preferences.setRanksFriendsDefault(true)
        assertEquals(RankScope.Friends, landing.scope.value)

        friends.value = false
        assertEquals(RankScope.Global, landing.scope.value)
        scope.cancel()
    }

    @Test
    fun `with the Games filter on a saved LinkedIn games default lands on LinkedIn games and follows a change`() = runTest {
        val scope = scope()
        val preferences = FakeUserPreferencesRepository(ranksLinkedInDefault = true)
        val landing = RanksLanding(preferences, flowOf(false), gamesFilter = true, scope)

        assertEquals(RanksSelection.Games.LinkedIn, landing.games.value)

        preferences.setRanksLinkedInDefault(false)
        assertEquals(RanksSelection.Games.All, landing.games.value)
        scope.cancel()
    }

    @Test
    fun `with the Games filter off a saved LinkedIn games default still lands on All games`() = runTest {
        val scope = scope()
        val preferences = FakeUserPreferencesRepository(ranksLinkedInDefault = true)

        val landing = RanksLanding(preferences, flowOf(false), gamesFilter = false, scope)

        assertEquals(RanksSelection.Games.All, landing.games.value)
        scope.cancel()
    }
}
