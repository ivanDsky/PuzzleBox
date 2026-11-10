package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.LegBasis.Hints
import com.ivandsky.puzzlebox.domain.LegBasis.Time
import com.ivandsky.puzzlebox.domain.LegVerdict.Draw
import com.ivandsky.puzzlebox.domain.LegVerdict.Loss
import com.ivandsky.puzzlebox.domain.LegVerdict.Pending
import com.ivandsky.puzzlebox.domain.LegVerdict.Void
import com.ivandsky.puzzlebox.domain.LegVerdict.Win
import com.ivandsky.puzzlebox.domain.MatchVerdict.Drawn
import com.ivandsky.puzzlebox.domain.MatchVerdict.Lost
import com.ivandsky.puzzlebox.domain.MatchVerdict.Won
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Every SC3 case (spec A2, A3, A10), one row each: a match, the results seen so far, the viewer and a fixed now in,
 * the full [Outcome] out. `alpha` ranks time then hints; `beta` ranks hints then time, as Strands does.
 */
@RunWith(Parameterized::class)
class ComputeMatchOutcomeTest(private val row: Row) {

    @Test
    fun `decides the outcome`() {
        val outcome = ComputeMatchOutcome(row.match, row.results, row.me, row.now) { rankOrderOf(it) }
        assertEquals(row.expected, outcome)
    }

    class Row(
        private val name: String,
        val match: Match,
        val results: List<LegResult>,
        val now: Instant,
        val expected: Outcome,
        val me: String,
    ) {
        override fun toString() = name
    }

    companion object {
        private const val ME = "me"
        private const val SAM = "sam"

        private val alpha = MatchLeg(LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 1), 1), authorUid = null)
        private val beta = MatchLeg(LegRef.Daily(TestGames.beta, LocalDate.of(2026, 11, 2), 2), authorUid = null)

        private fun match(
            vararg legs: MatchLeg,
            opponent: Player? = Player(SAM, "Sam", null),
            cancelled: Boolean = false,
            declined: Boolean = false,
        ) = Match(
            code = ShareCode.parse("K7Q2MX")!!,
            formatVersion = Match.FORMAT_VERSION,
            createdAt = Instant.parse("2026-11-08T10:00:00Z"),
            deadlineHours = 24,
            challenger = Player(ME, "Me", null),
            invitedUid = SAM,
            opponent = opponent,
            legs = legs.toList(),
            includeFriends = true,
            cancelled = cancelled,
            declined = declined,
            rematchOf = null,
            progress = emptyMap(),
        )

        private val deadline = match(alpha).deadline
        private val beforeDeadline = deadline.minusSeconds(3_600)
        private val inGrace = deadline.plusSeconds(3_600)
        private val atClose = match(alpha).closesAt
        private val early = beforeDeadline.minusSeconds(3_600).toEpochMilli()

        private fun result(uid: String, leg: Int, timeMs: Long, hints: Int, at: Long, gaveUp: Boolean = false) =
            LegResult(uid, leg, timeMs, hints, at, gaveUp)

        private fun mine(leg: Int, timeMs: Long, hints: Int = 0, at: Long = early) = result(ME, leg, timeMs, hints, at)
        private fun sams(leg: Int, timeMs: Long, hints: Int = 0, at: Long = early) = result(SAM, leg, timeMs, hints, at)
        private fun gaveUp(uid: String, leg: Int) = result(uid, leg, timeMs = 0, hints = 0, at = early, gaveUp = true)

        private fun rankOrderOf(game: GameId) =
            if (game == TestGames.beta) RankOrder.HintsThenTime else RankOrder.TimeThenHints

        private fun outcome(
            vararg legs: LegVerdict,
            mine: Int,
            theirs: Int,
            verdict: MatchVerdict?,
            settled: Boolean = true,
        ) = Outcome(legs.toList(), mine, theirs, settled, verdict)

        private fun row(
            name: String,
            match: Match,
            results: List<LegResult>,
            now: Instant,
            expected: Outcome,
            me: String = ME,
        ) = Row(name, match, results, now, expected, me)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Row> = listOf(
            row(
                "time win", match(alpha), listOf(mine(0, 60_000), sams(0, 75_000)), beforeDeadline,
                outcome(Win(Time), mine = 1, theirs = 0, verdict = Won),
            ),
            row(
                "time decides before hints: the slower player loses despite fewer hints",
                match(alpha), listOf(mine(0, 60_000, hints = 3), sams(0, 75_000, hints = 0)), beforeDeadline,
                outcome(Win(Time), mine = 1, theirs = 0, verdict = Won),
            ),
            row(
                "hints win under hints then time: more time, fewer hints",
                match(beta), listOf(mine(0, 90_000, hints = 0), sams(0, 60_000, hints = 2)), beforeDeadline,
                outcome(Win(Hints), mine = 1, theirs = 0, verdict = Won),
            ),
            row(
                "whole-second tie is broken by hints",
                match(alpha), listOf(mine(0, 61_900, hints = 1), sams(0, 61_100, hints = 0)), beforeDeadline,
                outcome(Loss(Hints), mine = 0, theirs = 1, verdict = Lost),
            ),
            row(
                "whole-second tie with equal hints is a draw",
                match(alpha), listOf(mine(0, 61_900, hints = 1), sams(0, 61_100, hints = 1)), beforeDeadline,
                outcome(Draw, mine = 0, theirs = 0, verdict = Drawn),
            ),
            row(
                "identical scores draw the leg",
                match(beta), listOf(mine(0, 45_000, hints = 2), sams(0, 45_000, hints = 2)), beforeDeadline,
                outcome(Draw, mine = 0, theirs = 0, verdict = Drawn),
            ),
            row(
                "equal wins over an even leg count draw the match",
                match(alpha, beta),
                listOf(mine(0, 60_000), sams(0, 70_000), mine(1, 60_000, hints = 2), sams(1, 70_000, hints = 1)),
                beforeDeadline,
                outcome(Win(Time), Loss(Hints), mine = 1, theirs = 1, verdict = Drawn),
            ),
            row(
                "missing leg before close is pending while decided legs already count",
                match(alpha, beta), listOf(mine(0, 60_000), sams(0, 70_000), mine(1, 60_000)), beforeDeadline,
                outcome(Win(Time), Pending, mine = 1, theirs = 0, verdict = null, settled = false),
            ),
            row(
                "missing legs at close lose on the first key of each game's order",
                match(alpha, beta), listOf(mine(0, 60_000), mine(1, 60_000)), atClose,
                outcome(Win(Time), Win(Hints), mine = 2, theirs = 0, verdict = Won),
            ),
            row(
                "both missing at close is a draw",
                match(alpha), emptyList(), atClose,
                outcome(Draw, mine = 0, theirs = 0, verdict = Drawn),
            ),
            row(
                "leg authored by the opponent is void and the match settles on the rest",
                match(alpha.copy(authorUid = SAM), alpha),
                listOf(mine(0, 10_000), sams(0, 90_000), mine(1, 80_000), sams(1, 70_000)),
                beforeDeadline,
                outcome(Void, Loss(Time), mine = 0, theirs = 1, verdict = Lost),
            ),
            row(
                "leg authored by me is void too",
                match(alpha.copy(authorUid = ME)), emptyList(), beforeDeadline,
                outcome(Void, mine = 0, theirs = 0, verdict = Drawn),
            ),
            row(
                "late post completed by the deadline counts inside grace",
                match(alpha), listOf(mine(0, 60_000), sams(0, 50_000, at = deadline.toEpochMilli())), inGrace,
                outcome(Loss(Time), mine = 0, theirs = 1, verdict = Lost),
            ),
            row(
                "result completed after the deadline is ignored inside grace",
                match(alpha), listOf(mine(0, 60_000), sams(0, 50_000, at = deadline.toEpochMilli() + 1)), inGrace,
                outcome(Pending, mine = 0, theirs = 0, verdict = null, settled = false),
            ),
            row(
                "result completed after the deadline is ignored at close, a forfeit",
                match(alpha), listOf(mine(0, 60_000), sams(0, 50_000, at = deadline.toEpochMilli() + 1)), atClose,
                outcome(Win(Time), mine = 1, theirs = 0, verdict = Won),
            ),
            row(
                "giving up against a finished result loses at once",
                match(beta), listOf(gaveUp(ME, 0), sams(0, 300_000, hints = 4)), beforeDeadline,
                outcome(Loss(Hints), mine = 0, theirs = 1, verdict = Lost),
            ),
            row(
                "both gave up is a draw at once",
                match(alpha), listOf(gaveUp(ME, 0), gaveUp(SAM, 0)), beforeDeadline,
                outcome(Draw, mine = 0, theirs = 0, verdict = Drawn),
            ),
            row(
                "giving up while the opponent has not played stays pending",
                match(alpha), listOf(gaveUp(ME, 0)), beforeDeadline,
                outcome(Pending, mine = 0, theirs = 0, verdict = null, settled = false),
            ),
            row(
                "giving up while the opponent never played is a draw at close",
                match(alpha), listOf(gaveUp(ME, 0)), atClose,
                outcome(Draw, mine = 0, theirs = 0, verdict = Drawn),
            ),
            row(
                "ledger 07: with two results for one leg of mine, the first in list order wins",
                match(alpha), listOf(mine(0, 60_000), mine(0, 30_000), sams(0, 45_000)), beforeDeadline,
                outcome(Loss(Time), mine = 0, theirs = 1, verdict = Lost),
            ),
            row(
                "ledger 07: with two results for one leg of theirs, the first in list order wins",
                match(alpha), listOf(mine(0, 60_000), sams(0, 75_000), sams(0, 30_000)), beforeDeadline,
                outcome(Win(Time), mine = 1, theirs = 0, verdict = Won),
            ),
            row(
                "cancelled match is all void, settled, no verdict",
                match(alpha, beta, cancelled = true), listOf(mine(0, 60_000), sams(0, 70_000)), beforeDeadline,
                outcome(Void, Void, mine = 0, theirs = 0, verdict = null),
            ),
            row(
                "declined match is all void, settled, no verdict",
                match(alpha, declined = true), emptyList(), beforeDeadline,
                outcome(Void, mine = 0, theirs = 0, verdict = null),
            ),
            row(
                "unclaimed match at the deadline is all void, settled, no verdict",
                match(alpha, beta, opponent = null), listOf(mine(0, 60_000)), deadline,
                outcome(Void, Void, mine = 0, theirs = 0, verdict = null),
            ),
            row(
                "unclaimed match before the deadline is pending",
                match(alpha, beta, opponent = null), listOf(mine(0, 60_000)), beforeDeadline,
                outcome(Pending, Pending, mine = 0, theirs = 0, verdict = null, settled = false),
            ),
            row(
                "the opponent sees the mirrored settled outcome",
                match(alpha.copy(authorUid = SAM), alpha),
                listOf(mine(0, 10_000), sams(0, 90_000), mine(1, 80_000), sams(1, 70_000)),
                beforeDeadline,
                outcome(Void, Win(Time), mine = 1, theirs = 0, verdict = Won),
                me = SAM,
            ),
            row(
                "the opponent sees the mirrored pending outcome",
                match(alpha, beta), listOf(mine(0, 60_000), sams(0, 70_000), mine(1, 60_000)), beforeDeadline,
                outcome(Loss(Time), Pending, mine = 0, theirs = 1, verdict = null, settled = false),
                me = SAM,
            ),
        )
    }
}
