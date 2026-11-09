package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reveal's mapping from a [MatchView], with fixed inputs; Ann is the viewer, Bob the opponent. */
class RevealLegsTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val zip = GameId("zip")
    private val strands = GameId("strands")
    private val createdAt = Instant.parse("2026-11-09T10:00:00Z")
    private val day = LocalDate.of(2026, 11, 9)

    private fun legRef(index: Int, game: GameId = zip): LegRef = LegRef.Daily(game, day, 400 + index)

    private fun view(
        legs: List<LegRef>,
        verdicts: List<LegVerdict>,
        mine: List<OutboxResult> = emptyList(),
        theirs: List<LegSeen> = legs.map { LegSeen.NotPlayed },
    ): MatchView {
        val match = Match(
            code = code,
            formatVersion = Match.FORMAT_VERSION,
            createdAt = createdAt,
            deadlineHours = 24,
            challenger = ann,
            invitedUid = null,
            opponent = bob,
            legs = legs.map { MatchLeg(it, authorUid = null) },
            includeFriends = false,
            cancelled = false,
            declined = false,
            rematchOf = null,
            progress = emptyMap(),
        )
        val outcome = Outcome(verdicts, verdicts.count { it is LegVerdict.Win }, verdicts.count { it is LegVerdict.Loss }, true, null)
        return MatchView(match, "ann", mine, theirs, outcome, local = null)
    }

    private fun myRow(leg: Int, timeMs: Long = 58_000, hints: Int = 0, gaveUp: Boolean = false, state: ResultPostState = ResultPostState.Posted) =
        OutboxResult(code, LegResult("ann", leg, timeMs, hints, completedAtMs = 1, gaveUp = gaveUp), state, attempts = 0)

    private fun theirs(leg: Int, timeMs: Long = 72_000, hints: Int = 0, gaveUp: Boolean = false) =
        LegSeen.Revealed(LegResult("bob", leg, timeMs, hints, completedAtMs = 1, gaveUp = gaveUp))

    private fun plan(view: MatchView, order: RankOrder = RankOrder.TimeThenHints) =
        revealPlan(view, nameOf = { if (it == zip) "Zip" else "Strands" }, rankOrderOf = { order })

    /** The basis line of a one-puzzle duel with the given verdict and sides. */
    private fun basis(
        verdict: LegVerdict,
        mine: OutboxResult?,
        theirs: LegSeen,
        order: RankOrder = RankOrder.TimeThenHints,
    ) = plan(view(listOf(legRef(0)), listOf(verdict), listOfNotNull(mine), listOf(theirs)), order).cards.single().basis

    @Test
    fun mySideIsATimeWhenIFinishedAndGaveUpWhenIGaveUp() {
        val legs = List(3) { legRef(it) }
        val view = view(
            legs,
            verdicts = listOf(LegVerdict.Win(LegBasis.Time), LegVerdict.Loss(LegBasis.Time), LegVerdict.Loss(LegBasis.Time)),
            mine = listOf(myRow(0, timeMs = 58_000, hints = 1), myRow(1, timeMs = 0, gaveUp = true)),
        )

        val sides = plan(view).legs.map { it.mine }

        assertEquals(listOf(DuelSideState.Time(58_000, 1), DuelSideState.GaveUp, DuelSideState.NotPlayed), sides)
    }

    @Test
    fun aResultOfMineThatDoesNotCountIsNotShownAsATime() {
        val legs = List(2) { legRef(it) }
        val view = view(
            legs,
            verdicts = listOf(LegVerdict.Loss(LegBasis.Time), LegVerdict.Win(LegBasis.Time)),
            mine = listOf(myRow(0, state = ResultPostState.Late), myRow(1, state = ResultPostState.Pending)),
            theirs = listOf(theirs(0), LegSeen.NotPlayed),
        )

        val sides = plan(view).legs.map { it.mine }

        assertEquals(listOf(DuelSideState.NotPlayed, DuelSideState.Time(58_000, 0)), sides)
    }

    @Test
    fun theirSideIsATimeWhenRevealedSealedWhenOnlyPlayedAndNotPlayedWhenAbsent() {
        val legs = List(4) { legRef(it) }
        val view = view(
            legs,
            verdicts = List(4) { LegVerdict.Draw },
            theirs = listOf(theirs(0, timeMs = 88_000, hints = 2), LegSeen.Played, LegSeen.NotPlayed, theirs(3, gaveUp = true)),
        )

        val sides = plan(view).legs.map { it.theirs }

        assertEquals(
            listOf(DuelSideState.Time(88_000, 2), DuelSideState.Sealed, DuelSideState.NotPlayed, DuelSideState.GaveUp),
            sides,
        )
    }

    @Test
    fun verdictsComeFromTheOutcomeNotRecomputed() {
        // My time is far worse, yet the domain rule named me the winner (hints): the card carries that verdict.
        val view = view(
            legs = listOf(legRef(0), legRef(1)),
            verdicts = listOf(LegVerdict.Win(LegBasis.Hints), LegVerdict.Draw),
            mine = listOf(myRow(0, timeMs = 300_000, hints = 0), myRow(1, timeMs = 10_000)),
            theirs = listOf(theirs(0, timeMs = 20_000, hints = 3), theirs(1, timeMs = 90_000)),
        )

        val verdicts = plan(view).legs.map { it.verdict }

        assertEquals(listOf(LegVerdict.Win(LegBasis.Hints), LegVerdict.Draw), verdicts)
    }

    @Test
    fun basisLineNamesSecondsFasterFewerHintsSameSecondAndNoResult() {
        val time = LegBasis.Time
        val hints = LegBasis.Hints

        assertEquals("14 s faster", basis(LegVerdict.Win(time), myRow(0, 58_000), theirs(0, 72_000)))
        assertEquals("14 s faster", basis(LegVerdict.Loss(time), myRow(0, 72_000), theirs(0, 58_000)))
        assertEquals("1 s faster", basis(LegVerdict.Win(time), myRow(0, 59_900), theirs(0, 60_100)))
        assertEquals("1 s faster", basis(LegVerdict.Win(time), myRow(0, 59_000), theirs(0, 60_900)))
        assertEquals("Fewer hints · 0 vs 2", basis(LegVerdict.Win(hints), myRow(0, hints = 0), theirs(0, hints = 2), RankOrder.HintsThenTime))
        assertEquals("Fewer hints · 1 vs 3", basis(LegVerdict.Loss(hints), myRow(0, hints = 3), theirs(0, hints = 1), RankOrder.HintsThenTime))
        assertEquals("Same whole second · no point", basis(LegVerdict.Draw, myRow(0, 58_100), theirs(0, 58_900)))
        assertEquals("No result from Bob · counts for Ann", basis(LegVerdict.Win(time), myRow(0), LegSeen.NotPlayed))
        assertEquals("No result from Bob · counts for Ann", basis(LegVerdict.Win(time), myRow(0), theirs(0, gaveUp = true)))
        assertEquals("No result from Ann · counts for Bob", basis(LegVerdict.Loss(time), null, theirs(0)))
        assertEquals("No result from either player · no point", basis(LegVerdict.Draw, null, LegSeen.NotPlayed))
        assertEquals("Doesn't count for either player", basis(LegVerdict.Void, myRow(0), theirs(0)))
    }

    @Test
    fun aPendingVerdictIsNotRaced() {
        val view = view(
            legs = List(3) { legRef(it) },
            verdicts = listOf(LegVerdict.Win(LegBasis.Time), LegVerdict.Pending, LegVerdict.Draw),
            mine = listOf(myRow(0), myRow(2)),
            theirs = listOf(theirs(0), LegSeen.Played, theirs(2, timeMs = 58_000)),
        )

        val cards = plan(view).cards

        assertEquals(listOf("Zip #400", "Zip #402"), cards.map { it.title })
        assertTrue(cards.none { it.leg.verdict == LegVerdict.Pending })
    }

    @Test
    fun aStrandsCardShowsTheHintsAsTheFigure() {
        val view = view(
            legs = listOf(legRef(0, strands)),
            verdicts = listOf(LegVerdict.Win(LegBasis.Hints)),
            mine = listOf(myRow(0, timeMs = 120_000, hints = 1)),
            theirs = listOf(theirs(0, timeMs = 90_000, hints = 3)),
        )

        val card = plan(view, RankOrder.HintsThenTime).cards.single()

        assertEquals(RankOrder.HintsThenTime, card.rankOrder)
        assertEquals("Fewer hints · 1 vs 3", card.basis)
    }

    @Test
    fun aFriendsPuzzleHasNoNumberAndTitlesAsAFriendsPuzzle() {
        val custom = LegRef.Custom(zip, ShareCode.parse("ABCD23")!!)
        val view = view(
            legs = listOf(custom, legRef(1)),
            verdicts = listOf(LegVerdict.Draw, LegVerdict.Draw),
        )

        assertEquals(listOf("Friend's puzzle", "Zip #401"), plan(view).cards.map { it.title })
    }

    @Test
    fun startsAtSummaryWhenAlreadySeenOrReducedMotion() {
        val seen = Instant.parse("2026-11-09T12:00:00Z")

        assertTrue(RevealStart.of(reducedMotion = true, revealSeenAt = null).startAtEnd)
        assertTrue(RevealStart.of(reducedMotion = false, revealSeenAt = seen).startAtEnd)
        assertTrue(RevealStart.of(reducedMotion = true, revealSeenAt = seen).startAtEnd)
        assertFalse(RevealStart.of(reducedMotion = false, revealSeenAt = null).startAtEnd)
    }

    @Test
    fun marksSeenOnlyWhenNotYetSeen() {
        val seen = Instant.parse("2026-11-09T12:00:00Z")

        assertTrue(RevealStart.of(reducedMotion = false, revealSeenAt = null).markSeen)
        assertTrue(RevealStart.of(reducedMotion = true, revealSeenAt = null).markSeen)
        assertFalse(RevealStart.of(reducedMotion = false, revealSeenAt = seen).markSeen)
    }
}
