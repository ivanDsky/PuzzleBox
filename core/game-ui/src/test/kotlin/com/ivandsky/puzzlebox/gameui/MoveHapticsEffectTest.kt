package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S2: a game's classified move plays its one haptic, only with PlayFeel on and never for the board as first seen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MoveHapticsEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val played = ArrayList<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    private var snapshot by mutableIntStateOf(0)

    /** Read by the host only: a write recomposes it without touching [snapshot]. */
    private var hostState by mutableIntStateOf(0)
    private var compositions = 0

    private fun setEffect(playFeel: Boolean, classify: (Int, Int) -> MoveHaptic?) {
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics, LocalPlayFeel provides playFeel) {
                val host = hostState
                compositions++
                // A lambda that captures composition state is a new instance each time, as a real caller's may be.
                MoveHapticsEffect({ snapshot + host * 0 }, classify)
            }
        }
        composeRule.waitForIdle()
    }

    private fun change(to: Int) {
        composeRule.runOnIdle { snapshot = to }
        composeRule.waitForIdle()
    }

    /** Even numbers place, odd ones are refused, a step back is not a move. */
    private val byParity = { before: Int, after: Int ->
        when {
            after < before -> null
            after % 2 == 0 -> MoveHaptic.Placed
            else -> MoveHaptic.Rejected
        }
    }

    @Test
    fun `a placed move ticks and a rejected one buzzes`() {
        setEffect(playFeel = true, byParity)

        change(2)
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), played)

        change(3)
        assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.Reject), played)
    }

    @Test
    fun `two refusals in a row each buzz`() {
        setEffect(playFeel = true, byParity)

        change(1)
        change(3)

        assertEquals(listOf(HapticFeedbackType.Reject, HapticFeedbackType.Reject), played)
    }

    @Test
    fun `a change the game does not classify plays nothing`() {
        snapshot = 4
        setEffect(playFeel = true, byParity)

        change(2)

        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    @Test
    fun `each change is classified against the one before it`() {
        val seen = ArrayList<Pair<Int, Int>>()
        setEffect(playFeel = true) { before, after ->
            seen += before to after
            null
        }

        change(5)
        change(7)

        assertEquals(listOf(0 to 5, 5 to 7), seen)
    }

    @Test
    fun `the board as first composed plays nothing`() {
        snapshot = 2
        setEffect(playFeel = true) { _, _ -> MoveHaptic.Placed }

        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    @Test
    fun `a recomposition with the snapshot unchanged plays nothing`() {
        setEffect(playFeel = true) { _, _ -> MoveHaptic.Placed }
        val before = compositions

        composeRule.runOnIdle { hostState = 1 }
        composeRule.waitForIdle()

        assertEquals("the host recomposed", before + 1, compositions)
        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    @Test
    fun `a change of the snapshot does not recompose the caller`() {
        setEffect(playFeel = true, byParity)
        val before = compositions

        change(2)

        assertEquals(listOf(HapticFeedbackType.TextHandleMove), played)
        assertEquals(before, compositions)
    }

    @Test
    fun `with PlayFeel off nothing plays`() {
        setEffect(playFeel = false, byParity)

        change(2)
        change(3)

        assertEquals(emptyList<HapticFeedbackType>(), played)
    }
}
