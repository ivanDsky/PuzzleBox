package com.ivandsky.puzzlebox.train.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.train.FakePracticeBoard
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.LINE_LOCK
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PracticePaneTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scope = CoroutineScope(Job())
    private val board = FakePracticeBoard(LINE_LOCK, BoardKind.Focus, puzzle(1), scope, onTrainedTurn = {})
    private var closed = 0
    private var tryAgain = 0
    private var tryAnother = 0

    @After
    fun tearDown() = scope.coroutineContext[Job]!!.cancel()

    private fun show(
        state: PracticeState,
        progress: TechniqueProgress = TechniqueProgress(Mark.Practicing, inRow = 2, answeredAtMs = 1L),
        chapter: PracticeChapter = standInChapter(),
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PracticePane(
                    chapter = chapter,
                    state = state,
                    progress = progress,
                    showBack = true,
                    onClose = { closed++ },
                    board = { _, modifier -> StandInGrid(modifier) },
                    onTryAgain = { tryAgain++ },
                    onTryAnother = { tryAnother++ },
                )
            }
        }
    }

    private fun showBoard(
        progress: TechniqueProgress = TechniqueProgress(Mark.Practicing, inRow = 2, answeredAtMs = 1L),
        chapter: PracticeChapter = standInChapter(),
    ) = show(PracticeState.Practicing(board), progress, chapter)

    private fun say(status: PracticeStatus) {
        board.say(status)
        composeRule.waitForIdle()
    }

    private fun assertBand(title: String, line: String) {
        composeRule.onNodeWithTag("train.status").assertTextContains(title).assertTextContains(line, substring = true)
    }

    /** Catches a band that says nothing before the first move, or one that does not name the technique the player is after. */
    @Test
    fun `before a move the band asks for the next move that uses the technique`() {
        showBoard()

        composeRule.onNodeWithTag("train.practice").assertIsDisplayed()
        assertBand("Find the next move", "Moves that use Line lock count")
        composeRule.onNodeWithTag("train.idea").assertIsDisplayed()
    }

    /** Catches a Right that says a move counts when it is on an easier technique, or one that does not say why it does not. */
    @Test
    fun `a Right band tells a trained move from an easier one`() {
        showBoard()

        say(PracticeStatus.Right(standInSpotlight(), trained = true))
        assertBand("Right", "That uses Line lock, so it counts. Find the next move.")

        say(PracticeStatus.Right(standInSpotlight(), trained = false))
        assertBand("Right", "does not count toward Line lock")
    }

    /** Catches a mid-step square that says Right, or a count that is not the squares left. */
    @Test
    fun `a Keep going band counts the squares left in the step`() {
        showBoard()

        say(PracticeStatus.KeepGoing(left = 2, trained = true))
        assertBand("Keep going", "2 more in this step.")

        say(PracticeStatus.KeepGoing(left = 1, trained = true))
        assertBand("Keep going", "1 more in this step.")
    }

    /** Catches an undone easier move that does not name the technique to look for, and a Wrong that does not say why. */
    @Test
    fun `an undone move says what to look for or why it broke the puzzle`() {
        showBoard()

        say(PracticeStatus.Easier("Naked single", standInSpotlight()))
        assertBand("An easier move exists", "Look for Naked single first.")

        say(PracticeStatus.Wrong("Two queens would touch."))
        assertBand("Not there, undone", "Two queens would touch.")
    }

    /** Catches a Show me that is not the board's: the board applies the deduction, the host only asks. */
    @Test
    fun `Show me asks the board to show the next move`() {
        showBoard()

        composeRule.onNodeWithText("Show me").performClick()
        say(PracticeStatus.Shown(standInSpotlight()))

        assertEquals(1, board.showMeCount)
        assertBand("Here is the next move", "Look at the board, then tap Got it.")
    }

    /** Catches an explanation with no way to dismiss it, a Got it that is not the board's, or a Show me that stays live under one. */
    @Test
    fun `Got it dismisses an explanation through the board and Show me waits`() {
        showBoard()
        composeRule.onNodeWithTag("train.gotIt").assertDoesNotExist()
        composeRule.onNodeWithTag("train.showMe").assertIsEnabled()

        say(PracticeStatus.Shown(standInSpotlight()))
        composeRule.onNodeWithTag("train.showMe").assertIsNotEnabled()
        composeRule.onNodeWithTag("train.gotIt").performClick()

        assertEquals(1, board.gotItCount)
        assertEquals(0, board.showMeCount)
    }

    /** Catches a count that is not the stored one, or a mastered technique that still shows a row. */
    @Test
    fun `the title row counts the trained moves in a row, and says Mastered`() {
        showBoard(TechniqueProgress(Mark.Practicing, inRow = 3, answeredAtMs = 1L))
        composeRule.onNodeWithTag("train.count").assertTextEquals("3 of 5 in a row")
    }

    @Test
    fun `a mastered technique says Mastered in place of a count`() {
        showBoard(TechniqueProgress(Mark.Mastered, inRow = 5, answeredAtMs = 1L))
        composeRule.onNodeWithTag("train.count").assertTextEquals("Mastered")
    }

    /** Catches a title row that does not say which board of the chapter is up, or a first-technique chapter that claims a review board. */
    @Test
    fun `the title row says which board of the chapter is up`() {
        showBoard(chapter = standInChapter(board = 2, boards = 5))
        composeRule.onNodeWithTag("train.board").assertTextEquals("Board 2 of 5")
    }

    @Test
    fun `the first technique's chapter has four boards`() {
        showBoard(chapter = standInChapter(board = 4, boards = 4))
        composeRule.onNodeWithTag("train.board").assertTextEquals("Board 4 of 4")
    }

    /** Catches a finished board that keeps asking for the next move, or a Done band that does not say which board finished. */
    @Test
    fun `a finished board says Done with its number and takes no Show me`() {
        showBoard(chapter = standInChapter(board = 2, boards = 5))

        say(PracticeStatus.Done(standInSpotlight()))

        assertBand("Done", "Board 2 of 5 finished.")
        composeRule.onNodeWithTag("train.showMe").assertIsNotEnabled()
    }

    /** Catches a finished page with no way back to the list, one wired to another action, or one that still offers a next board. */
    @Test
    fun `the finished page offers Back to techniques only`() {
        show(PracticeState.Finished)

        composeRule.onNodeWithTag("train.finished").assertIsDisplayed()
        composeRule.onNodeWithText("Next board").assertDoesNotExist()
        composeRule.onNodeWithText("Back to techniques").performClick()
        assertEquals(listOf(0, 0, 1), listOf(tryAgain, tryAnother, closed))
    }

    /** Catches the load pages wired to each other's action: a fetch that opens a puzzle, an open that fetches, a close that does neither. */
    @Test
    fun `each load page's action is its own`() {
        show(PracticeState.NeedsConnection)
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(listOf(1, 0, 0), listOf(tryAgain, tryAnother, closed))
    }

    @Test
    fun `the can't-load page opens a puzzle on the device`() {
        show(PracticeState.CantLoad(alternative = 918))
        composeRule.onNodeWithText("Try another puzzle").performClick()
        assertEquals(listOf(0, 1, 0), listOf(tryAgain, tryAnother, closed))
    }

    @Test
    fun `the empty page goes back to the techniques`() {
        show(PracticeState.Empty)
        composeRule.onNodeWithText("Back to techniques").performClick()
        assertEquals(listOf(0, 0, 1), listOf(tryAgain, tryAnother, closed))
    }
}
