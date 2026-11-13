package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.compete.reveal.RevealMachine.Companion.IntroMs
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The effect on a live [RevealMachine] composed in the screen: a recording [HapticFeedback] through [LocalHapticFeedback]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RevealHapticsEffectTest {

    @get:Rule
    val rule = createComposeRule()

    private val buzzes = mutableListOf<HapticFeedbackType>()

    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            buzzes += hapticFeedbackType
        }
    }

    private var mounted by mutableStateOf<RevealMachine?>(null)

    @Test
    fun skippingOrReducedMotionBuzzesNothing() {
        show()

        mount(RevealMachine(listOf(win), reduced = true))
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)

        val skipped = RevealMachine(listOf(win), reduced = false)
        mount(skipped)
        rule.runOnIdle { skipped.advance(IntroMs - 100) }
        rule.waitForIdle()
        rule.runOnIdle { skipped.skip() }
        rule.waitForIdle()
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)
    }

    @Test
    fun watchAgainBuzzesTheBeatsAgain() {
        show()
        val first = RevealMachine(listOf(win), reduced = false)
        mount(first)
        play(first)
        val firstBuzzes = buzzes.toList()
        assertEquals(listOf(Soft, Confirm, LongPress), firstBuzzes)

        buzzes.clear()
        val again = RevealMachine(listOf(win), reduced = false)
        mount(again)
        play(again)
        assertEquals(firstBuzzes, buzzes.toList())
    }

    @Test
    fun oneFrameAcrossTwoBeatsBuzzesOnce() {
        show()
        val machine = RevealMachine(listOf(win), reduced = false)
        mount(machine)

        // One frame carries the VS lock-in and the puzzle's lock: the stronger of the two, once.
        rule.runOnIdle { machine.advance(IntroMs + WinLockMs + 100) }
        rule.waitForIdle()
        assertEquals(listOf(Confirm), buzzes)
    }

    private fun show() {
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    mounted?.let { RevealHapticsEffect(it) }
                }
            }
        }
    }

    /** Mounts [machine] in place of the last one, as a Watch again does with a new machine. */
    private fun mount(machine: RevealMachine) {
        rule.runOnIdle { mounted = machine }
        rule.waitForIdle()
    }

    /** Plays [machine] to End one [Step] at a time, letting the effect collect each state. */
    private fun play(machine: RevealMachine) {
        var steps = 0
        while (machine.state.value.phase != RevealPhase.End) {
            check(steps++ < MaxSteps) { "the reveal never reached End" }
            rule.runOnIdle { machine.advance(Step) }
            rule.waitForIdle()
        }
    }

    private fun leg(mine: DuelSideState, theirs: DuelSideState, verdict: LegVerdict) = LegReveal(GameId("zip"), mine, theirs, verdict)

    private fun time(ms: Long) = DuelSideState.Time(ms, hints = 0)

    private val win = leg(time(30_000), time(48_000), LegVerdict.Win(LegBasis.Time))

    private companion object {
        const val Step = 50L
        const val MaxSteps = 10_000
        /** The winner's ticker run in [win]: `2400 × 30 / 48` is 1500 ms, inside the clamp. */
        const val WinLockMs = 1_500L
        val Soft = HapticFeedbackType.TextHandleMove
        val Confirm = HapticFeedbackType.Confirm
        val LongPress = HapticFeedbackType.LongPress
    }
}
