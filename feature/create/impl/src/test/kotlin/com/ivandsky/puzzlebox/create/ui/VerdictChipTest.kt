package com.ivandsky.puzzlebox.create.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The draft row's chip shows a Unique's grade; a needs-guessing Unique is blocked, so it never reads as plain "Unique". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerdictChipTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a needs-guessing Unique reads as needs guessing in the warn tone`() {
        val guessing = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), needsGuessing = true)
        composeRule.setContent { PuzzleBoxTheme { VerdictChip(guessing) } }

        composeRule.onNodeWithText("Unique · needs guessing").assertExists()
        composeRule.onNodeWithText("Unique").assertDoesNotExist()
        assertEquals(VerdictTone.Warn, guessing.tone())
    }

    @Test
    fun `a graded Unique adds its level in the ok tone`() {
        val graded = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), Difficulty(MediumRung))
        composeRule.setContent { PuzzleBoxTheme { VerdictChip(graded) } }

        composeRule.onNodeWithText("Unique · Medium").assertExists()
        assertEquals(VerdictTone.Ok, graded.tone())
    }

    private object MediumRung : LadderRung {
        override val id: String = "test.lineLock"
        override val level: Difficulty.Level = Difficulty.Level.Medium
        override val captionKey: String = "test.lineLock"
    }
}
