package com.ivandsky.puzzlebox.domain

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** F20: the rank shown outside the hub follows the player's Friends/Global setting, behind the hub's flag. */
class ObserveResultRankFriendsTest {

    private val tango = GameId("tango")
    private val day = LocalDate.of(2026, 11, 7)
    private val me = Identity("me", "Me", null)
    private val registry = TestGames.registry(listOf(tango))
    private val identity = FakeIdentityService(initial = me)
    private val matches = FakeMatchRepository().apply { signedInUid = me.uid }
    private val days = FakeLinkedInDays()
    private val preferences = FakeUserPreferencesRepository(ranksFriendsDefault = true)

    private fun entry(uid: String, seconds: Int) = LeaderboardEntry(uid, uid, "tango/2026-11-07/12", seconds * 1_000L, 0, 1_700_000_000_000)

    // Me third of four on the whole board; only b is a friend.
    private val repo = FakeLeaderboardRepository(
        mapOf(LeaderboardRef("tango", day) to listOf(entry("a", 30), entry("b", 40), entry("me", 50), entry("c", 60))),
    )

    private fun rank(vararg flags: ReleaseFlag) = ObserveResultRank(
        ObserveLeaderboard(identity, repo, registry, FakeGameCalendar(day)),
        registry,
        ObserveFriendDay(preferences, FakeReleaseFlags(*flags), ObserveFriendUids(matches, identity), ObserveLinkedInDay(days, registry)),
    )(tango, day)

    private suspend fun befriend(uid: String) {
        val code = checkNotNull(ShareCode.parse("AAAAA2"))
        matches.create(MatchDraft(code, 24, Player(me.uid, "Me", null), null, listOf(MatchLeg(LegRef.Daily(tango, day, 12), null)), true, null))
        matches.signedInUid = uid
        matches.claim(code, Player(uid, uid, null))
        matches.signedInUid = me.uid
    }

    /** A connected day whose connections board was read: one connection at [seconds] and one with a hidden time. */
    private fun connection(seconds: Int) = days.emit(
        tango,
        day,
        LinkedInDayData(
            link = LinkState.Connected("me"),
            delta = 0,
            boardInWindow = true,
            inFlight = false,
            lastSync = null,
            own = LinkedInOwnState.Unknown,
            ghosts = listOf(
                LinkedInGhost("member-1", "Member 1", seconds * 1_000L, hintFree = true, flawless = false),
                LinkedInGhost("member-2", "Member 2", null, hintFree = true, flawless = false),
            ),
            boardFetchedAtMs = 1L,
            owner = null,
        ),
    )

    private suspend fun ReceiveTurbine<ResultRank>.lastRanked(): ResultRank.Ranked {
        var item = awaitItem()
        while (item !is ResultRank.Ranked) item = awaitItem()
        return item
    }

    @Test
    fun `Friends ranks the player among friends and the connections with a time`() = runTest {
        befriend("b")
        connection(seconds = 20)
        rank(ReleaseFlag.LeaderboardHub, ReleaseFlag.LinkedInAccount).test {
            // The connection, then b, then me: a and c are no friends and the hidden time takes no place.
            assertEquals(ResultRank.Ranked(position = 3, count = 3, topPercent = 100), lastRanked())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Friends with LinkedIn off ranks among friends only`() = runTest {
        befriend("b")
        connection(seconds = 20)
        rank(ReleaseFlag.LeaderboardHub).test {
            assertEquals(ResultRank.Ranked(position = 2, count = 2, topPercent = 100), lastRanked())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Global ranks on the whole board and a switch to Friends re-ranks`() = runTest {
        befriend("b")
        preferences.setRanksFriendsDefault(false)
        rank(ReleaseFlag.LeaderboardHub).test {
            assertEquals(ResultRank.Ranked(position = 3, count = 4, topPercent = 75), lastRanked())
            preferences.setRanksFriendsDefault(true)
            assertEquals(ResultRank.Ranked(position = 2, count = 2, topPercent = 100), lastRanked())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with the hub off the rank stays the whole board's whatever the setting`() = runTest {
        befriend("b")
        rank().test {
            assertEquals(ResultRank.Hidden, awaitItem())
            assertEquals(ResultRank.Ranked(position = 3, count = 4, topPercent = 75), awaitItem())
            expectNoEvents()
        }
    }
}
