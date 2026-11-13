package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The glyph-only pill keeps a blocked (needs-guessing) Unique from reading as publishable. */
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
}
