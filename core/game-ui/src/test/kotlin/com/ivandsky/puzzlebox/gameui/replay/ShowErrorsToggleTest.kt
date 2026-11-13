package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F7 (chrome side): the Show errors icon toggle flips what every board of a replay page is told, single and compare. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShowErrorsToggleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)
    private var showErrors by mutableStateOf(true)

    /** Writes what each board was told; the compare page draws two of them. */
    private fun fakeBoard(errors: Boolean = true) = object : GameReplayBoard {
        override val pieces = "pieces"
        override val hasErrors = errors

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
            Text("errors ${options.showErrors}", modifier.testTag(BOARD))
        }
    }

    private val board = fakeBoard()

    private fun state() =
        composeRule.onNodeWithTag(ReplayTags.SHOW_ERRORS).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    private fun showPage() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplayPage(timeline, board, context = "Fixture · #1", onBack = {}, showErrors = showErrors, onShowErrorsChange = { showErrors = it })
            }
        }
    }

    @Test
    fun `the toggle is named and says on while errors show`() {
        showPage()

        composeRule.onNodeWithTag(ReplayTags.SHOW_ERRORS).assertContentDescriptionEquals(ReplayCopy.SHOW_ERRORS)
        assertEquals(ReplayCopy.ON, state())
    }

    @Test
    fun `a tap turns errors off on the board and on again`() {
        showPage()
        composeRule.onNodeWithTag(BOARD).assertTextEquals("errors true")

        composeRule.onNodeWithTag(ReplayTags.SHOW_ERRORS).performClick()

        assertEquals(false, showErrors)
        assertEquals(ReplayCopy.OFF, state())
        composeRule.onNodeWithTag(BOARD).assertTextEquals("errors false")

        composeRule.onNodeWithTag(ReplayTags.SHOW_ERRORS).performClick()

        assertEquals(true, showErrors)
        assertEquals(ReplayCopy.ON, state())
        composeRule.onNodeWithTag(BOARD).assertTextEquals("errors true")
    }

    @Test
    fun `the compare page's toggle reaches both boards`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplayComparePage(
                    mine = timeline,
                    theirs = timeline,
                    board = board,
                    names = CompareNames("You", "Maya"),
                    layout = CompareLayout.Twin,
                    context = "Fixture · Duel",
                    onBack = {},
                    showErrors = showErrors,
                    onShowErrorsChange = { showErrors = it },
                )
            }
        }

        composeRule.onNodeWithTag(ReplayTags.SHOW_ERRORS).performClick()

        assertEquals(false, showErrors)
        composeRule.onAllNodesWithTag(BOARD).assertCountEquals(2)
        composeRule.onAllNodesWithTag(BOARD)[0].assertTextEquals("errors false")
        composeRule.onAllNodesWithTag(BOARD)[1].assertTextEquals("errors false")
    }

    @Test
    fun `F21 the single page hides the switch when the game has no errors`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplayPage(timeline, fakeBoard(errors = false), context = "Fixture · #1", onBack = {}, showErrors = showErrors, onShowErrorsChange = { showErrors = it })
            }
        }

        composeRule.onAllNodesWithTag(ReplayTags.SHOW_ERRORS).assertCountEquals(0)
        composeRule.onNodeWithTag(BOARD).assertExists()
    }

    @Test
    fun `F21 the compare page hides the switch when the game has no errors`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplayComparePage(
                    mine = timeline,
                    theirs = timeline,
                    board = fakeBoard(errors = false),
                    names = CompareNames("You", "Maya"),
                    layout = CompareLayout.Twin,
                    context = "Fixture · Duel",
                    onBack = {},
                    showErrors = showErrors,
                    onShowErrorsChange = { showErrors = it },
                )
            }
        }

        composeRule.onAllNodesWithTag(ReplayTags.SHOW_ERRORS).assertCountEquals(0)
        assertEquals(true, showErrors)
    }

    private companion object {
        const val BOARD = "fake-board"
    }
}
