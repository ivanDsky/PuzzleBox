package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lineup panel as drawn: full at ten puzzles, the sheet opened from a row or the Add row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class LineupPanelTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithBrands() {
        startKoin { modules(module { single { newDuelBrands() } }) }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(editor: LineupEditor, onEdit: ((LineupEditor) -> LineupEditor) -> Unit = {}) {
        rule.setContent {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { LineupPanel(editor, GAMES, includeFriends = false, onEdit = onEdit) }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `ten puzzles show every row and the limit note instead of the Add row`() {
        show(LineupEditor(LineupDraft(List(Match.MAX_LEGS) { AnyGame(ZIP) })))

        rule.onNodeWithTag("duel.lineup.header").assertTextEquals("Puzzles, in order · 10 of 10")
        rule.onNodeWithTag("duel.lineup.row.10", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("duel.lineup.max").assertExists()
        rule.onNodeWithTag("duel.lineup.add").assertDoesNotExist()
    }

    @Test
    fun `fewer than ten show the Add row for the next number and no limit note`() {
        show(LineupEditor.opening(GAMES))

        rule.onNodeWithTag("duel.lineup.header").assertTextEquals("Puzzles, in order · 3 of 10")
        rule.onNodeWithTag("duel.lineup.add").assertExists()
        rule.onNodeWithTag("duel.lineup.max").assertDoesNotExist()
    }

    @Test
    fun `the last puzzle's remove button is disabled`() {
        show(LineupEditor(LineupDraft(listOf(AnyGame(TANGO)))))

        rule.onNodeWithTag("duel.lineup.remove.1").assertIsNotEnabled()
    }

    @Test
    fun `tapping the Add row asks for the sheet on the next row`() {
        var editor = LineupEditor.opening(GAMES)
        show(editor) { edit -> editor = edit(editor) }

        rule.onNodeWithTag("duel.lineup.add").performClick()
        assertEquals(3, editor.target)
    }

    @Test
    fun `an open sheet lists the games and a tap picks one`() {
        var editor = LineupEditor.opening(GAMES).openAdd()
        rule.setContent {
            PuzzleBoxTheme { LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) }) }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.sheet").assertIsDisplayed()
        rule.onNodeWithTag("duel.lineup.sheet.game.${TANGO.value}").performClick()

        assertEquals(AnyGame(TANGO), editor.draft.entries.last())
    }
}
