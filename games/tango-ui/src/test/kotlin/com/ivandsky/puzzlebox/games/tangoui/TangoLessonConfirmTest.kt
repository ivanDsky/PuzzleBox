package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S2 in the lessons: Tango's lesson board never reaches the play screen's solve frame, so the tutorial host
 * confirms the solve once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TangoLessonConfirmTest {

    @get:Rule
    val rule = createComposeRule()

    private val buzzes = ArrayList<HapticFeedbackType>()

    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            buzzes += hapticFeedbackType
        }
    }

    /** Lesson one in the tutorial host, solved by its intended moves: the Confirms it played through the success beat. */
    private fun confirmsOnSolve(): Int {
        val real = TangoPlayUi(HintMode.Stepwise(Dispatchers.Unconfined))
        var lesson: GamePlayController? = null
        val ui = object : GamePlayUi by real {
            override fun createLessonController(env: PlayEnvironment): GamePlayController =
                real.createLessonController(env).also { lesson = it }
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                PuzzleBoxTheme(darkTheme = false) {
                    TutorialHost(ui = ui, session = TutorialSession(ui.tutorial), onFinished = {})
                }
            }
        }
        advance(SETTLE_MS)
        rule.runOnIdle {
            solve(lesson as TangoPlayController)
            Snapshot.sendApplyNotifications()
        }
        advance(PAST_THE_BEAT_MS)
        return buzzes.count { it == HapticFeedbackType.Confirm }
    }

    /** Lesson one's taps: a moon (two taps) and two suns. */
    private fun solve(c: TangoPlayController) = listOf(4, 4, 6, 8).forEach(c::onTap)

    private fun advance(ms: Long) = repeat((ms / STEP_MS).toInt()) {
        rule.mainClock.advanceTimeBy(STEP_MS)
        rule.waitForIdle()
    }

    @Test
    fun `a lesson solve confirms once`() {
        assertEquals(1, confirmsOnSolve())
    }

    private companion object {
        const val STEP_MS = 16L
        const val SETTLE_MS = 96L

        /** Past the host's 900 ms success beat, into the next lesson. */
        const val PAST_THE_BEAT_MS = 1_600L
    }
}
