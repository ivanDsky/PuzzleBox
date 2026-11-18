package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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

/** The verdict card's grade shows a level with its mark and word; Needs guessing keeps its Hint mark and no level mark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerdictGradeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a graded Unique shows its level's mark and word in the grade`() {
        val graded = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), Difficulty(HardRung))
        composeRule.setContent { PuzzleBoxTheme { VerdictCard(graded, onCheckAgain = {}) } }

        composeRule.onNode(hasTestTag("difficulty.hard") and hasAnyAncestor(hasTestTag(GRADE)), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Hard").assertIsDisplayed()
    }

    @Test
    fun `a Unique that needs guessing shows no level mark`() {
        val guessing = Verdict.Unique(Solution(listOf(1, 7, 8, 14)), Difficulty(HardRung), needsGuessing = true)
        composeRule.setContent { PuzzleBoxTheme { VerdictCard(guessing, onCheckAgain = {}) } }

        composeRule.onNodeWithTag(GRADE).assertIsDisplayed()
        composeRule.onNodeWithTag("difficulty.hard", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Needs guessing").assertIsDisplayed()
    }

    private object HardRung : LadderRung {
        override val id: String = "test.hard"
        override val level: Difficulty.Level = Difficulty.Level.Hard
        override val captionKey: String = "test.hard"
    }

    private companion object {
        const val GRADE = "create.verdict.grade"
    }
}
