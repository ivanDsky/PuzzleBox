package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
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
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel { AddPuzzleViewModel(pickerOf(emptyList()), NO_LOOKUP) }
            })
        }
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
    fun `a fixed panel, the Daily duel's, shows its rows with no Add row, and the last row cannot be removed`() {
        var editor by mutableStateOf(LineupEditor(LineupDraft(listOf(AnyGame(ZIP), AnyGame(QUEENS)))))
        rule.setContent {
            PuzzleBoxTheme {
                LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) }, fixed = true)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.row.2", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("duel.lineup.add").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.max").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.row.1").performClick()
        assertEquals(null, editor.target)

        rule.onNodeWithTag("duel.lineup.remove.1").performClick()
        rule.waitForIdle()
        assertEquals(listOf<LineupEntry>(AnyGame(QUEENS)), editor.draft.entries)
        rule.onNodeWithTag("duel.lineup.remove.1").assertIsNotEnabled()
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

    @Test
    fun `dragging row 3 by its handle above row 1 makes the lineup read 3, 1, 2 and the summary follow`() {
        var editor by mutableStateOf(LineupEditor.opening(GAMES))
        val names = mapOf(ZIP to "Zip", TANGO to "Tango", QUEENS to "Queens")
        rule.setContent {
            PuzzleBoxTheme {
                Column {
                    Text(
                        lineupSummary(editor.entries, 24, false, Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC, Locale.US) { names.getValue(it) },
                        Modifier.testTag("summary"),
                    )
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) })
                }
            }
        }
        rule.waitForIdle()
        val rowHeight = rule.onNodeWithTag("duel.lineup.row.3").fetchSemanticsNode().size.height.toFloat()

        rule.onNodeWithTag("duel.lineup.handle.3", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, -rowHeight * 2.5f / DRAG_STEPS)) }
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(QUEENS, ZIP, TANGO), editor.entries.map { it.gameId })
        rule.onNodeWithTag("duel.lineup.row.1").assertTextContains("Any Queens", substring = true)
        rule.onNodeWithTag("duel.lineup.row.3").assertTextContains("Any Tango", substring = true)
        rule.onNodeWithTag("summary").assertTextContains("Best of 3 · Queens, Zip, Tango", substring = true)
    }

    @Test
    fun `a short drag that stays within its slot leaves the order as it was`() {
        var editor by mutableStateOf(LineupEditor.opening(GAMES))
        show(editor) { edit -> editor = edit(editor) }
        val rowHeight = rule.onNodeWithTag("duel.lineup.row.1").fetchSemanticsNode().size.height.toFloat()

        rule.onNodeWithTag("duel.lineup.handle.1", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, rowHeight * 0.3f / DRAG_STEPS)) }
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(ZIP, TANGO, QUEENS), editor.entries.map { it.gameId })
    }

    /** The panel as NewDuelSheet holds it: in a column that scrolls, taller than its window. */
    private fun showScrolling(onEdit: ((LineupEditor) -> LineupEditor) -> Unit): androidx.compose.foundation.ScrollState {
        val scroll = androidx.compose.foundation.ScrollState(0)
        rule.setContent {
            PuzzleBoxTheme {
                Column(Modifier.height(400.dp).verticalScroll(scroll)) {
                    LineupPanel(LineupEditor.opening(GAMES), GAMES, includeFriends = false, onEdit = onEdit)
                    Spacer(Modifier.height(1_000.dp))
                }
            }
        }
        rule.waitForIdle()
        return scroll
    }

    @Test
    fun `in a scrolling column a handle drag reorders and the column does not scroll`() {
        var editor = LineupEditor.opening(GAMES)
        val scroll = showScrolling { edit -> editor = edit(editor) }
        val rowHeight = rule.onNodeWithTag("duel.lineup.row.1").fetchSemanticsNode().size.height.toFloat()

        rule.onNodeWithTag("duel.lineup.handle.1", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, rowHeight * 2.5f / DRAG_STEPS)) }
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(TANGO, QUEENS, ZIP), editor.entries.map { it.gameId })
        assertEquals(0, scroll.value)
    }

    @Test
    fun `a drag the system cancels puts the row back and reorders nothing`() {
        var editor = LineupEditor.opening(GAMES)
        val scroll = showScrolling { edit -> editor = edit(editor) }
        val rowHeight = rule.onNodeWithTag("duel.lineup.row.1").fetchSemanticsNode().size.height.toFloat()

        rule.onNodeWithTag("duel.lineup.handle.1", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, rowHeight * 2.5f / DRAG_STEPS)) }
            cancel()
        }
        rule.waitForIdle()

        assertEquals(listOf(ZIP, TANGO, QUEENS), editor.entries.map { it.gameId })
        assertEquals(0, scroll.value)
    }

    private companion object {
        const val DRAG_STEPS = 10
    }
}
