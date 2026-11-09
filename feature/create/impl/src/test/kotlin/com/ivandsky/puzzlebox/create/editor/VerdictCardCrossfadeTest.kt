package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The card's face crossfades on a new verdict while the game's fix list stays one list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerdictCardCrossfadeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the issues slot is drawn once while the face crossfades`() {
        var verdict: Verdict by mutableStateOf(Verdict.Unique(Solution(listOf(0))))
        composeRule.setContent {
            PuzzleBoxTheme {
                VerdictCard(
                    verdict = verdict,
                    onCheckAgain = {},
                    issues = { modifier -> Box(modifier.height(24.dp).testTag(ISSUES)) },
                )
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { verdict = Verdict.None(conflicts = setOf(0, 1)) }
        composeRule.mainClock.advanceTimeBy(FRAMES_INTO_FADE_MS)
        ShadowLooper.idleMainLooper()
        composeRule.mainClock.advanceTimeBy(FRAMES_INTO_FADE_MS)

        assertEquals(1, composeRule.onAllNodesWithTag(ISSUES).fetchSemanticsNodes().size)
    }

    @Test
    fun `a new Multiple straight after another shows its own cell count`() {
        fun multiple(cells: IntRange) = Verdict.Multiple(Solution(listOf(0)), Solution(listOf(1)), cells.toSet())
        var verdict: Verdict by mutableStateOf(multiple(5..19))
        composeRule.setContent {
            PuzzleBoxTheme { VerdictCard(verdict = verdict, onCheckAgain = {}, issues = {}) }
        }
        composeRule.onNodeWithText("15 cells differ, marked on the board").assertExists()

        composeRule.runOnIdle { verdict = multiple(23..26) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("4 cells differ, marked on the board").assertExists()
        composeRule.onNodeWithText("15 cells differ, marked on the board").assertDoesNotExist()
    }

    private companion object {
        const val ISSUES = "test.verdict.issues"

        /** Well inside the crossfade, so both faces are still composed. */
        const val FRAMES_INTO_FADE_MS = 64L
    }
}
