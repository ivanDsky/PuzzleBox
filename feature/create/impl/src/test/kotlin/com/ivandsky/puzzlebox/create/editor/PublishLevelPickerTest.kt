package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The level control (96): Easy, Medium, Hard and Expert as four chips, the chosen one marked, each a 48dp target, with
 * "Proposed: Hard" beside them so a changed level shows its difference; a tap reports the level; a running publish or a
 * frozen choice turns the chips off; the details show it only when a choice is handed in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublishLevelPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val followed = LevelChoice(proposed = Level.Hard, chosen = Level.Hard)
    private val changed = LevelChoice(proposed = Level.Hard, chosen = Level.Easy)

    @Test
    fun `it offers the four levels, marks the chosen one and says what was proposed`() {
        composeRule.setContent { PuzzleBoxTheme { PublishLevelPicker(changed, {}) } }

        chip(Level.Easy).assertIsSelected()
        for (other in listOf(Level.Medium, Level.Hard, Level.Expert)) chip(other).assertIsNotSelected()
        composeRule.onNodeWithText("Proposed:").assertExists()
        proposed().assert(hasAnyDescendant(hasText("Hard")))
    }

    @Test
    fun `a followed proposal still says Proposed`() {
        composeRule.setContent { PuzzleBoxTheme { PublishLevelPicker(followed, {}) } }

        chip(Level.Hard).assertIsSelected()
        composeRule.onNodeWithText("Proposed:").assertExists()
        proposed().assert(hasAnyDescendant(hasText("Hard")))
    }

    @Test
    fun `each chip shows its level's mark and the proposal line shows the proposed level's`() {
        composeRule.setContent { PuzzleBoxTheme { PublishLevelPicker(changed, {}) } }

        for (level in Level.entries) {
            composeRule.onNode(mark(level) and hasAnyAncestor(hasTestTag(chipTag(level))), useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNode(mark(Level.Hard) and hasAnyAncestor(hasTestTag("create.details.level.proposed")), useUnmergedTree = true).assertIsDisplayed()
    }

    private fun chipTag(level: Level) = "create.details.level.${level.name.lowercase()}"

    private fun chip(level: Level) = composeRule.onNodeWithTag(chipTag(level))

    private fun proposed() = composeRule.onNodeWithTag("create.details.level.proposed")

    private fun mark(level: Level) = hasTestTag("difficulty.${level.name.lowercase()}")

    @Test
    fun `a tap reports the level`() {
        val picked = mutableListOf<Level>()
        composeRule.setContent { PuzzleBoxTheme { PublishLevelPicker(followed, picked::add) } }

        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(listOf(Level.Expert), picked)
    }

    @Test
    fun `each chip is at least a 48dp target`() {
        composeRule.setContent { PuzzleBoxTheme { PublishLevelPicker(followed, {}) } }

        for (tag in listOf("easy", "medium", "hard", "expert")) {
            composeRule.onNodeWithTag("create.details.level.$tag").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun `a running publish turns the chips off`() {
        composeRule.setContent { PuzzleBoxTheme { PublishLevelPicker(followed, {}, enabled = false) } }

        composeRule.onNodeWithText("Easy").assertIsNotEnabled()
    }

    @Test
    fun `the details show the control only when a choice is handed in`() {
        composeRule.setContent { PuzzleBoxTheme { Details(level = null) } }
        assertTrue("no proposal or flag off: no control", composeRule.onAllNodes(hasTestTag("create.details.level")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the details show the control, report the pick, and freeze it with the visibility`() {
        val picked = mutableListOf<Level>()
        composeRule.setContent { PuzzleBoxTheme { Details(followed, picked::add, locked = false) } }

        composeRule.onNodeWithTag("create.details.level").assertExists()
        composeRule.onNodeWithText("Medium").performClick()

        assertEquals(listOf(Level.Medium), picked)
    }

    @Test
    fun `a frozen choice turns the chips off in the details`() {
        composeRule.setContent { PuzzleBoxTheme { Details(followed, locked = true) } }

        composeRule.onNodeWithText("Easy").assertIsNotEnabled()
    }

    @Composable
    private fun Details(level: LevelChoice?, onLevel: (Level) -> Unit = {}, locked: Boolean = false) {
        PublishDetails(
            title = "Castle",
            details = PublishSession.Details(authorName = "Ivan", visibility = PublishVisibility.Choice(Visibility.Public, locked)),
            signedIn = true,
            onRename = {},
            onRollTitle = { "Amber Falcon" },
            onAuthorName = {},
            onNote = {},
            modifier = Modifier.fillMaxWidth(),
            level = level,
            onLevel = onLevel,
        )
    }
}
