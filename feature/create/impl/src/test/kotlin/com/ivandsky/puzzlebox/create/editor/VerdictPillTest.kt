package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The glyph-only pill keeps a blocked (needs-guessing) Unique from reading as publishable; a Unique with a level
 * shows the level's mark, before the label on the wide pill and in place of the tick on the compact one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerdictPillTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a compact pill never shows the tick for a needs-guessing Unique`() {
        val guessing = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), needsGuessing = true)
        composeRule.setContent { PuzzleBoxTheme { VerdictPill(guessing, onClick = {}, compact = true) } }

        composeRule.onNodeWithText("✓").assertDoesNotExist()
        composeRule.onNodeWithText("!").assertExists()
    }

    @Test
    fun `a compact pill shows the tick for a publishable Unique`() {
        val unique = Verdict.Unique(Solution(listOf(1, 7, 8, 14)))
        composeRule.setContent { PuzzleBoxTheme { VerdictPill(unique, onClick = {}, compact = true) } }

        composeRule.onNodeWithText("✓").assertExists()
    }

    @Test
    fun `a compact pill shows a graded Unique's level mark in place of the tick`() {
        val graded = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), Difficulty(HardRung))
        composeRule.setContent { PuzzleBoxTheme { VerdictPill(graded, onClick = {}, compact = true) } }

        composeRule.onNodeWithTag("difficulty.hard", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("✓").assertDoesNotExist()
    }

    @Test
    fun `a wide pill shows a graded Unique's level mark before its label`() {
        val graded = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), Difficulty(HardRung))
        composeRule.setContent { PuzzleBoxTheme { VerdictPill(graded, onClick = {}) } }

        composeRule.onNodeWithTag("difficulty.hard", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Unique · Hard").assertIsDisplayed()
    }

    @Test
    fun `a wide pill shows no level mark for a Unique without a level`() {
        val plain = Verdict.Unique(Solution(listOf(1, 7, 8, 14)))
        composeRule.setContent { PuzzleBoxTheme { VerdictPill(plain, onClick = {}) } }

        composeRule.onNodeWithTag("difficulty.hard", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Unique").assertIsDisplayed()
    }

    private object HardRung : LadderRung {
        override val id: String = "test.hard"
        override val level: Difficulty.Level = Difficulty.Level.Hard
        override val captionKey: String = "test.hard"
    }
}
