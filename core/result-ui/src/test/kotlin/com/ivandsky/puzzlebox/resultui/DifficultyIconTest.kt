package com.ivandsky.puzzlebox.resultui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.game.editor.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every level has its own bar mark, and the label shows that mark beside the level's word. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DifficultyIconTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `each level maps to its own mark`() {
        val expected = mapOf(
            Difficulty.Level.Easy to PuzzleIcons.LevelEasy,
            Difficulty.Level.Medium to PuzzleIcons.LevelMedium,
            Difficulty.Level.Hard to PuzzleIcons.LevelHard,
            Difficulty.Level.Expert to PuzzleIcons.LevelExpert,
        )
        assertEquals(Difficulty.Level.entries.toSet(), expected.keys)
        expected.forEach { (level, icon) -> assertEquals(level.name, icon, level.icon) }
        assertEquals(expected.size, Difficulty.Level.entries.map { it.icon.name }.toSet().size)
    }

    @Test
    fun `the label shows the level's mark and its word`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                DifficultyLabel(Difficulty.Level.Hard, MaterialTheme.typography.labelLarge, MaterialTheme.colorScheme.onSurface)
            }
        }
        composeRule.onNodeWithTag("difficulty.hard").assertIsDisplayed()
        composeRule.onNodeWithText("Hard").assertIsDisplayed()
    }
}
