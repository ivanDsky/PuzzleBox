package com.ivandsky.puzzlebox.play.domain.moments

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.ranks.RankedBoard
import com.ivandsky.puzzlebox.domain.ranks.rankBoard
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** L1: which moments a counted solve earned, strongest first. */
class DetectMomentsTest {

    private val ref = LeaderboardRef("alpha", LocalDate.of(2026, 11, 16))

    /** A counted solve that earns nothing: slower than every threshold, mid-set, mid-streak, no friend board. */
    private val plain = SolveFacts(
        timeMs = 400_000,
        hints = 0,
        playAgain = false,
        practice = false,
        newBest = false,
        bestHintFreeMs = null,
        doneToday = 1,
        totalToday = 3,
        streakBefore = 2,
        streak = 3,
    )

    private fun entry(uid: String, timeMs: Long) =
        LeaderboardEntry(uid, uid.replaceFirstChar(Char::uppercase), "alpha/${ref.day}/1", timeMs, 0, 1L)

    private fun board(vararg entries: LeaderboardEntry): RankedBoard =
        rankBoard(ref, entries.toList(), RankOrder.TimeThenHints, entries.size)

    private fun friendDay(before: RankedBoard, after: RankedBoard) =
        SolveFacts.FriendDay(viewerUid = "me", before = before, after = after)

    @Test
    fun `a solve that earned nothing has no moments`() {
        assertEquals(emptyList<Moment>(), detectMoments(plain))
    }

    @Test
    fun `a new best is a personal best`() {
        assertEquals(listOf(Moment.PersonalBest), detectMoments(plain.copy(newBest = true)))
    }

    @Test
    fun `the first hint-free solve under several thresholds earns only the tightest`() {
        assertEquals(listOf(Moment.FirstSubX(60_000)), detectMoments(plain.copy(timeMs = 58_000)))
    }

    @Test
    fun `a time exactly on a threshold has not gone under it`() {
        assertEquals(listOf(Moment.FirstSubX(90_000)), detectMoments(plain.copy(timeMs = 60_000)))
    }

    @Test
    fun `a threshold an earlier hint-free solve met does not fire again`() {
        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(timeMs = 58_000, bestHintFreeMs = 59_000)))
    }

    @Test
    fun `a threshold the earlier best only touched is still a first`() {
        assertEquals(listOf(Moment.FirstSubX(60_000)), detectMoments(plain.copy(timeMs = 58_000, bestHintFreeMs = 60_000)))
    }

    @Test
    fun `a hinted solve crosses no threshold`() {
        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(timeMs = 58_000, hints = 1)))
    }

    @Test
    fun `the fifth solve of five carries the day's total and four of five carry no full set`() {
        val total = Moment.FullSetDone.DayTotal(timeMs = 5 * 60_000L, hints = 2)
        assertEquals(listOf(Moment.FullSetDone(total)), detectMoments(plain.copy(doneToday = 5, totalToday = 5, dayTotal = total)))
        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(doneToday = 4, totalToday = 5, dayTotal = total)))
    }

    @Test
    fun `the solve that completes today's set is a full set`() {
        assertEquals(listOf(Moment.FullSetDone()), detectMoments(plain.copy(doneToday = 3, totalToday = 3)))
    }

    @Test
    fun `a day with no games has no full set`() {
        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(doneToday = 0, totalToday = 0)))
    }

    @Test
    fun `the solve that reaches 7, 30 or 100 days is a streak moment`() {
        for (days in listOf(7, 30, 100)) {
            assertEquals(listOf(Moment.Streak(days)), detectMoments(plain.copy(streakBefore = days - 1, streak = days)))
        }
    }

    @Test
    fun `a streak number this solve did not reach is no moment`() {
        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(streakBefore = 7, streak = 7)))
        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(streakBefore = 7, streak = 8)))
    }

    @Test
    fun `a place in the first three of at least four friends is a top three`() {
        val after = board(entry("ann", 10), entry("me", 20), entry("bea", 30), entry("cy", 40))

        assertEquals(listOf(Moment.TopThreeFriends(place = 2)), detectMoments(plain.copy(friends = friendDay(after, after))))
    }

    @Test
    fun `first of three friends is not a top three`() {
        val after = board(entry("me", 10), entry("ann", 20), entry("bea", 30))

        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(friends = friendDay(after, after))))
    }

    @Test
    fun `fourth of five friends is not a top three`() {
        val after = board(entry("ann", 10), entry("bea", 20), entry("cy", 30), entry("me", 40), entry("dee", 50))

        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(friends = friendDay(after, after))))
    }

    @Test
    fun `a friend above before the solve and below after it was passed`() {
        val before = board(entry("maya", 50))
        val after = board(entry("me", 40), entry("maya", 50))

        assertEquals(listOf(Moment.PassedFriend("maya", "Maya")), detectMoments(plain.copy(friends = friendDay(before, after))))
    }

    @Test
    fun `a friend the player's earlier place trailed and this solve beat was passed`() {
        val before = board(entry("maya", 50), entry("me", 60))
        val after = board(entry("me", 40), entry("maya", 50))

        assertEquals(listOf(Moment.PassedFriend("maya", "Maya")), detectMoments(plain.copy(friends = friendDay(before, after))))
    }

    @Test
    fun `a friend already below was not passed`() {
        val before = board(entry("me", 45), entry("maya", 50))
        val after = board(entry("me", 40), entry("maya", 50))

        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(friends = friendDay(before, after))))
    }

    @Test
    fun `a friend still above was not passed`() {
        val before = board(entry("maya", 50))
        val after = board(entry("maya", 50), entry("me", 60))

        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(friends = friendDay(before, after))))
    }

    @Test
    fun `a friend who posted only after the solve was not passed`() {
        val before = board()
        val after = board(entry("me", 40), entry("maya", 50))

        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(friends = friendDay(before, after))))
    }

    @Test
    fun `a friend tied with the player was not passed`() {
        val before = board(entry("maya", 50))
        val after = board(entry("maya", 50), entry("me", 50))

        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(friends = friendDay(before, after))))
    }

    @Test
    fun `of several passed friends the one directly below the player is named`() {
        val ahead = arrayOf(entry("dee", 10), entry("eve", 20), entry("fay", 30))
        val before = board(*ahead, entry("cy", 70), entry("ann", 50), entry("bea", 60))
        val after = board(*ahead, entry("cy", 70), entry("me", 40), entry("bea", 60), entry("ann", 50))

        assertEquals(listOf(Moment.PassedFriend("ann", "Ann")), detectMoments(plain.copy(friends = friendDay(before, after))))
    }

    @Test
    fun `a board the player is not on gives no friend moment`() {
        val before = board(entry("ann", 10), entry("bea", 20), entry("cy", 30), entry("dee", 40))

        assertEquals(emptyList<Moment>(), detectMoments(plain.copy(friends = friendDay(before, before))))
    }

    private val everything = plain.copy(
        timeMs = 58_000,
        newBest = true,
        doneToday = 3,
        streakBefore = 6,
        streak = 7,
        friends = friendDay(
            before = board(entry("ann", 60), entry("bea", 70), entry("cy", 80)),
            after = board(entry("me", 58), entry("ann", 60), entry("bea", 70), entry("cy", 80)),
        ),
    )

    @Test
    fun `moments come strongest first`() {
        assertEquals(
            listOf(
                Moment.PersonalBest,
                Moment.FirstSubX(60_000),
                Moment.TopThreeFriends(place = 1),
                Moment.PassedFriend("ann", "Ann"),
                Moment.FullSetDone(),
                Moment.Streak(7),
            ),
            detectMoments(everything),
        )
    }

    @Test
    fun `without a friend board the local moments still come`() {
        assertEquals(
            listOf(Moment.PersonalBest, Moment.FirstSubX(60_000), Moment.FullSetDone(), Moment.Streak(7)),
            detectMoments(everything.copy(friends = null)),
        )
    }

    @Test
    fun `a play again earns no moment`() {
        assertEquals(emptyList<Moment>(), detectMoments(everything.copy(playAgain = true)))
    }

    @Test
    fun `a practice puzzle earns no moment`() {
        assertEquals(emptyList<Moment>(), detectMoments(everything.copy(practice = true)))
    }
}
