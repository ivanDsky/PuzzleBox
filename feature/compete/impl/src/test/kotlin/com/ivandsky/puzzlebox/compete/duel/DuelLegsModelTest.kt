package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The puzzles screen's mapping from a [MatchView]; Ann is the viewer, Bob the opponent. */
class DuelLegsModelTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val zip = GameId("zip")
    private val createdAt = Instant.parse("2026-11-09T10:00:00Z")
    private val day = LocalDate.of(2026, 11, 9)

    private fun match(legs: Int, progress: Map<String, Set<Int>> = emptyMap()) = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = createdAt,
        deadlineHours = 24,
        challenger = ann,
        invitedUid = null,
        opponent = bob,
        legs = List(legs) { MatchLeg(LegRef.Daily(zip, day, 400 + it), authorUid = null) },
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = progress,
    )

    private fun mine(leg: Int, state: ResultPostState = ResultPostState.Posted, timeMs: Long = 102_000, hints: Int = 0, gaveUp: Boolean = false) =
        OutboxResult(code, LegResult("ann", leg, timeMs, hints, completedAtMs = 1, gaveUp = gaveUp), state, attempts = 0)

    private fun view(
        match: Match,
        mine: List<OutboxResult> = emptyList(),
        theirs: List<LegSeen> = match.legs.map { LegSeen.NotPlayed },
        settled: Boolean = false,
    ) = MatchView(match, "ann", mine, theirs, Outcome(emptyList(), 0, 0, settled, verdict = null), local = null)

    private fun model(
        view: MatchView,
        elapsed: Map<Int, Long> = emptyMap(),
        now: Instant = createdAt,
    ) = duelLegsModel(view, elapsed, now, nameOf = { "Zip" }, rankOrderOf = { RankOrder.TimeThenHints })

    @Test
    fun eachPuzzleStatusMapsFromOutboxAndSavedPlay() {
        val match = match(legs = 4)
        val view = view(match, mine = listOf(mine(2, timeMs = 102_000, hints = 1), mine(3, timeMs = 0, gaveUp = true)))

        val statuses = model(view, elapsed = mapOf(1 to 65_000L)).rows.map { it.status }

        assertEquals(
            listOf(LegStatus.New, LegStatus.InProgress(65_000), LegStatus.Solved(102_000, 1), LegStatus.GaveUp),
            statuses,
        )
    }

    @Test
    fun aFinishedPuzzleWinsOverItsLeftoverSavedPlay() {
        val view = view(match(legs = 1), mine = listOf(mine(0)))

        val row = model(view, elapsed = mapOf(0 to 30_000L)).rows.single()

        assertEquals(LegStatus.Solved(102_000, 0), row.status)
        assertNull(row.action)
    }

    @Test
    fun opponentLineIsSealedWhileIHaveNotFinishedAndRevealedAfterward() {
        val match = match(legs = 3)
        val theirResult = LegResult("bob", 2, 88_000, 2, completedAtMs = 1, gaveUp = false)
        val view = view(match, theirs = listOf(LegSeen.NotPlayed, LegSeen.Played, LegSeen.Revealed(theirResult)))

        val lines = model(view).rows.map { it.opponent }

        assertEquals(
            listOf(OpponentLine.NotPlayed, OpponentLine.Sealed, OpponentLine.Revealed(88_000, 2, gaveUp = false)),
            lines,
        )
    }

    @Test
    fun postingCaptionFollowsResultPostState() {
        val match = match(legs = 4)
        val view = view(
            match,
            mine = listOf(
                mine(0, ResultPostState.Pending),
                mine(1, ResultPostState.Late),
                mine(2, ResultPostState.LocalOnly),
                mine(3, ResultPostState.Posted),
            ),
        )

        val captions = model(view).rows.map { it.posting }

        assertEquals(listOf(PostingCaption.Pending, PostingCaption.Late, PostingCaption.LocalOnly, null), captions)
    }

    @Test
    fun footerIsWaitingWhenOnlyIAmDoneAndOffersTheRevealWhenRevealReady() {
        val allMine = listOf(mine(0), mine(1))
        val playing = view(match(legs = 2), mine = listOf(mine(0)))
        val waiting = view(match(legs = 2, progress = mapOf("bob" to setOf(0))), mine = allMine)
        val bothDone = view(match(legs = 2, progress = mapOf("bob" to setOf(0, 1))), mine = allMine)
        val pendingPost = view(
            match(legs = 2, progress = mapOf("bob" to setOf(0, 1))),
            mine = listOf(mine(0), mine(1, ResultPostState.Pending)),
        )
        val closed = view(match(legs = 2), mine = emptyList())
        val afterClose = waiting.match.closesAt

        assertEquals(DuelFooter.Playing, model(playing).footer)
        assertEquals(DuelFooter.Waiting, model(waiting).footer)
        assertEquals(DuelFooter.RevealReady, model(bothDone).footer)
        assertEquals(DuelFooter.Waiting, model(pendingPost).footer)
        assertEquals(DuelFooter.RevealReady, model(closed, now = afterClose).footer)
    }

    @Test
    fun startIsDisabledOnceTheOutcomeIsSettled() {
        val match = match(legs = 2)

        val open = model(view(match, settled = false), elapsed = mapOf(1 to 5_000L)).rows
        val settled = model(view(match, settled = true), elapsed = mapOf(1 to 5_000L)).rows

        assertEquals(LegAction.Start, open[0].action)
        assertTrue(open[0].actionEnabled)
        assertEquals(LegAction.Start, settled[0].action)
        assertFalse(settled[0].actionEnabled)
        assertEquals(LegAction.Resume, settled[1].action)
    }

    @Test
    fun startIsDisabledFromTheDeadlineButResumeStays() {
        val match = match(legs = 2)
        val before = model(view(match), elapsed = mapOf(1 to 5_000L), now = match.deadline - 1.seconds.toJavaDuration()).rows
        val after = model(view(match), elapsed = mapOf(1 to 5_000L), now = match.deadline).rows

        assertTrue(before[0].actionEnabled)
        assertFalse(after[0].actionEnabled)
        assertEquals(LegAction.Resume, after[1].action)
        assertTrue(after[1].actionEnabled)
    }

    @Test
    fun theCountdownRunsToTheDeadlineNotTheClose() {
        val match = match(legs = 2)
        val now = match.deadline - (23.hours + 59.minutes).toJavaDuration()

        // The grace after the deadline is for posting only: what the screen counts down to is the deadline.
        assertTrue(match.closesAt > match.deadline)
        assertEquals("Closes in 23 h 59 m", timeLeftText(match.deadline, now))
        assertEquals("Closed", timeLeftText(match.deadline, match.deadline))
        assertTrue(nudgeText("Bob", code, match.deadline, match.deadline).contains("has closed."))
    }

    @Test
    fun timeLeftRoundsDownToHoursAndMinutesAndSaysClosedAtZero() {
        val deadline = createdAt + 30.hours.toJavaDuration()
        val left = 17.hours + 42.minutes + 59.seconds

        assertEquals("Closes in 17 h 42 m", timeLeftText(deadline, deadline - left.toJavaDuration()))
        assertEquals("Closes in 42 m", timeLeftText(deadline, deadline - 42.minutes.toJavaDuration()))
        assertEquals("Closed", timeLeftText(deadline, deadline))
        assertEquals("Closed", timeLeftText(deadline, deadline + 5.minutes.toJavaDuration()))
        assertFalse(isClosed(deadline, deadline - 1.minutes.toJavaDuration()))
        assertTrue(isClosed(deadline, deadline))
    }

    @Test
    fun underAMinuteLeftIsStillOpenAndOnlyClosesAtTheDeadline() {
        val deadline = createdAt + 30.hours.toJavaDuration()
        val now = deadline - 30.seconds.toJavaDuration()

        assertEquals("Closes in <1 m", timeLeftText(deadline, now))
        assertFalse(isClosed(deadline, now))
        assertTrue(nudgeText("Bob", code, deadline, now).contains("closes in <1 m."))
    }

    @Test
    fun nudgeTextNamesTheOpponentAndTheTimeLeft() {
        val deadline = createdAt + 30.hours.toJavaDuration()
        val now = deadline - (17.hours + 42.minutes).toJavaDuration()

        val text = nudgeText("Bob", code, deadline, now)

        assertTrue(text.contains("Your duel with Bob closes in 17 h 42 m."))
        assertTrue(text.contains(code.matchLink))
    }
}
