package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Test

/** The buzzes a whole reveal makes, driven on [RevealMachine]'s virtual clock, one per [Step] of advance. */
class RevealHapticsTest {

    @Test
    fun theVsLockInBuzzesSoftOnce() {
        assertEquals(listOf(Soft), buzzesOf(listOf(leg(time(30_000), time(48_000), LegVerdict.Draw))))
    }

    @Test
    fun aPuzzleVerdictBuzzesSoftAndAWinOfMineBuzzesStronger() {
        val legs = listOf(
            leg(time(45_000), time(30_000), LegVerdict.Loss(LegBasis.Time)),
            leg(time(30_000), time(48_000), LegVerdict.Win(LegBasis.Time)),
        )

        // The VS lock-in, the puzzle they won, then the puzzle the viewer won: one duel verdict short of a win, no LongPress.
        assertEquals(listOf(Soft, Soft, Confirm), buzzesOf(legs))
    }

    @Test
    fun winningTheDuelBuzzesTheStrongest() {
        val legs = listOf(
            leg(time(30_000), time(48_000), LegVerdict.Win(LegBasis.Time)),
            leg(time(30_000), time(48_000), LegVerdict.Win(LegBasis.Time)),
        )

        assertEquals(listOf(Soft, Confirm, Confirm, LongPress), buzzesOf(legs))
    }

    @Test
    fun aLostOrDrawnDuelAndAnUnwonPuzzleBuzzNothing() {
        val lost = listOf(
            leg(time(30_000), time(48_000), LegVerdict.Draw),
            leg(time(45_000), time(30_000), LegVerdict.Loss(LegBasis.Time)),
        )
        val drawn = listOf(
            leg(time(30_000), time(48_000), LegVerdict.Win(LegBasis.Time)),
            leg(time(45_000), time(30_000), LegVerdict.Loss(LegBasis.Time)),
        )

        // A puzzle nobody wins buzzes nothing; a loss is only the soft tick of that puzzle's verdict, never a LongPress.
        assertEquals(listOf(Soft, Soft), buzzesOf(lost))
        assertEquals(listOf(Soft, Confirm, Soft), buzzesOf(drawn))
    }

    private fun buzzesOf(legs: List<LegReveal>): List<HapticFeedbackType> {
        val machine = RevealMachine(legs, reduced = false)
        val states = mutableListOf(machine.state.value)
        while (states.last().phase != RevealPhase.End) {
            check(states.size < MaxSteps) { "the reveal never reached End" }
            machine.advance(Step)
            states += machine.state.value
        }
        return states.zipWithNext().mapNotNull { (before, after) -> RevealHaptics.between(before, after) }
    }

    private fun leg(mine: DuelSideState, theirs: DuelSideState, verdict: LegVerdict) = LegReveal(GameId("zip"), mine, theirs, verdict)

    private fun time(ms: Long) = DuelSideState.Time(ms, hints = 0)

    private companion object {
        const val Step = 10L
        const val MaxSteps = 100_000
        val Soft = HapticFeedbackType.TextHandleMove
        val Confirm = HapticFeedbackType.Confirm
        val LongPress = HapticFeedbackType.LongPress
    }
}
