package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The add sheet's row: the size, then the level's mark beside its word, then the code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PickerRowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(puzzle: PickablePuzzle) {
        val brand = newDuelBrands().brandOf(QUEENS)
        rule.setContent { PuzzleBoxTheme { PickerRow(PickerEntry(puzzle, added = false), brand, onAdd = {}) } }
        rule.waitForIdle()
    }

    private fun levelOf(level: Level) = pickable(QUEENS, "K7Q2MX", "Crown", "Bo", PuzzleSource.Community, level = level)

    @Test
    fun `a labelled row shows the level's mark in the row, beside its word, and the size without the word`() {
        show(levelOf(Level.Expert))

        val row = hasAnyAncestor(hasTestTag("duel.lineup.pick.${levelOf(Level.Expert).entry.ref.wire}"))
        rule.onNode(hasTestTag("difficulty.expert") and row, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Expert").assertExists()
        rule.onNodeWithText("n8").assertExists()
    }

    @Test
    fun `a row with no level shows no mark`() {
        show(pickable(QUEENS, "K7Q2MX", "Crown", "Bo", PuzzleSource.Community))

        Level.entries.forEach { rule.onAllNodesWithTag("difficulty.${it.name.lowercase()}", useUnmergedTree = true).assertCountEquals(0) }
    }
}
