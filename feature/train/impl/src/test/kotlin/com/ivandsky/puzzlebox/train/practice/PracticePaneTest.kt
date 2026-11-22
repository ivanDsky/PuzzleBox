package com.ivandsky.puzzlebox.train.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
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
    private var skipped = 0
    private var nextChapter = 0
    private var practiceAgain = 0

    private fun sampleResult() = ChapterResult(
        technique = TrainerTechnique(LINE_LOCK, "Line lock", "A line is locked."),
        next = TrainerTechnique(TechniqueId("pointing-pair"), "Pointing pair", "A pair points."),
        outcome = ChapterOutcome.NewlyMastered,
        boards = 5,
        boardsDone = 5,
        rights = 9,
        shown = 1,
        slips = 2,
        undone = 3,
        best = 6,
        rows = List(5) { BoardRow(it + 1, BoardRowKind.Clean, 2) },
        allMastered = false,
        techniques = 4,
    )

    @After
    fun tearDown() = scope.coroutineContext[Job]!!.cancel()

    private fun show(
        state: PracticeState,
        progress: TechniqueProgress = TechniqueProgress(Mark.Practicing, inRow = 2, answeredAtMs = 1L),
        chapter: PracticeChapter = standInChapter(),
        hold: BoardHold? = null,
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
                    hold = hold,
                    onSkip = { skipped++ },
                    onNextChapter = { nextChapter++ },
                    onPracticeAgain = { practiceAgain++ },
                )
            }
        }
    }

    private fun showBoard(
        progress: TechniqueProgress = TechniqueProgress(Mark.Practicing, inRow = 2, answeredAtMs = 1L),
        chapter: PracticeChapter = standInChapter(),
        hold: BoardHold? = null,
    ) = show(PracticeState.Practicing(board), progress, chapter, hold)

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
        assertBand("Fits", "That is another technique's move. Only Line lock moves count here.")

        say(PracticeStatus.Right(standInSpotlight(), trained = false, technique = "Naked single"))
        assertBand("Fits", "That is a Naked single move. Only Line lock moves count here.")
    }

    /** Catches a review board that tells the player the same thing as a focus board, so a correct move undone for an easier one looks like a bug. */
    @Test
    fun `a review board says any technique up to the trained one may be used`() {
        showBoard(chapter = standInChapter(board = 5, boards = 5))
        assertBand("Find the next move", "Mixed board: use any technique up to Line lock. Easier moves come first.")
    }

    /** Catches a focus board of a chapter with a review board that already talks about mixing, or a first technique's last board that does. */
    @Test
    fun `a focus board is not a review board`() {
        showBoard(chapter = standInChapter(board = 4, boards = 5))
        assertBand("Find the next move", "Moves that use Line lock count")
    }

    @Test
    fun `the first technique's last board is not a review board`() {
        showBoard(chapter = standInChapter(board = 4, boards = 4))
        assertBand("Find the next move", "Moves that use Line lock count")
    }

    /** Catches a mid-move square that says Right, a count that is not the squares left, or a count that does not say what is counted. */
    @Test
    fun `a Keep going band counts the squares left in the step`() {
        showBoard()

        say(PracticeStatus.KeepGoing(left = 2, trained = true))
        assertBand("Keep going", "This move has 2 more squares. Find them.")

        say(PracticeStatus.KeepGoing(left = 1, trained = true))
        assertBand("Keep going", "This move has 1 more square. Find it.")

        say(PracticeStatus.KeepGoing(left = 4, trained = true, unit = "note"))
        assertBand("Keep going", "This move has 4 more notes. Find them.")
    }

    /** Catches an undone easier move that does not name the technique to look for, and a Wrong that does not say why. */
    @Test
    fun `an undone move says what to look for or why it broke the puzzle`() {
        showBoard()

        say(PracticeStatus.Easier("Naked single", standInSpotlight()))
        assertBand("An easier move exists", "Look for Naked single first.")

        say(PracticeStatus.Wrong("Two queens would touch."))
        assertBand("Undone", "Two queens would touch.")
    }

    /** Catches an "easier move" that names the technique being practiced, which reads as "I was doing that". */
    @Test
    fun `an undone move that needs the practiced technique says it cannot show it yet`() {
        showBoard()

        say(PracticeStatus.Easier("Line lock", standInSpotlight()))
        assertBand("Not yet", "Right square, but Line lock can't show it yet. Find a Line lock move first.")
    }

    /** Catches a Show me that is not the board's: the board applies the deduction, the host only asks. */
    @Test
    fun `Show me asks the board to show the next move`() {
        showBoard()

        composeRule.onNodeWithText("Show me").performClick()
        say(PracticeStatus.Shown(standInSpotlight()))

        assertEquals(1, board.showMeCount)
        assertBand("Here is the next move", "Look at the board, then tap Got it or the board to go on.")
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
    fun `the title row counts the streak of trained moves`() {
        showBoard(TechniqueProgress(Mark.Practicing, inRow = 3, answeredAtMs = 1L))
        composeRule.onNodeWithTag("train.count").assertTextEquals("Streak 3 of 5")
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
    fun `a finished board says it is done with its number and takes no Show me`() {
        showBoard(chapter = standInChapter(board = 2, boards = 5))

        say(PracticeStatus.Done(standInSpotlight()))

        composeRule.onNodeWithTag("train.status").assertTextContains("Board 2 of 5 done").assertTextContains("Next board in 2", substring = true)
        composeRule.onNodeWithTag("train.showMe").assertIsNotEnabled()
    }

    /** Catches a count that is not the whole second left, one that reaches 0, or a skip that is not the view model's. */
    @Test
    fun `the done band counts the whole seconds left and its button skips the wait`() {
        showBoard(hold = BoardHold(remainingMs = 1_001, waitsForTap = false))
        say(PracticeStatus.Done(null))
        composeRule.onNodeWithTag("train.status").assertTextContains("Next board in 2", substring = true)

        composeRule.onNodeWithTag("train.nextBoard").performClick()
        assertEquals(1, skipped)
    }

    @Test
    fun `the done band never counts below one`() {
        showBoard(hold = BoardHold(remainingMs = 40, waitsForTap = false))
        say(PracticeStatus.Done(null))
        composeRule.onNodeWithTag("train.status").assertTextContains("Next board in 1", substring = true)
    }

    /** Catches the last board's band promising another board, or a button that does not say it goes to the result. */
    @Test
    fun `the last board's band counts down to the chapter result`() {
        showBoard(chapter = standInChapter(board = 5, boards = 5))
        say(PracticeStatus.Done(null))

        composeRule.onNodeWithTag("train.status").assertTextContains("Board 5 of 5 done").assertTextContains("Result in 2", substring = true)
        composeRule.onNodeWithTag("train.nextBoard").assertTextContains("See result")
    }

    /** Catches a board that stops half-filled with no reason given. */
    @Test
    fun `a board that ended unsolved says the next move needs another technique`() {
        showBoard(chapter = standInChapter(board = 2, boards = 5))
        say(PracticeStatus.Done(null, needsOtherTechnique = true))

        composeRule.onNodeWithTag("train.status").assertTextContains("The next move needs another technique.", substring = true)
        composeRule.onNodeWithTag("train.status").assertTextContains("Next board in 2", substring = true)
    }

    @Test
    fun `a solved board does not say it needs another technique`() {
        showBoard(chapter = standInChapter(board = 2, boards = 5))
        say(PracticeStatus.Done(null))

        composeRule.onNodeWithText("needs another technique", substring = true).assertDoesNotExist()
    }

    /** Catches a screen reader user left with a countdown that moves on by itself. */
    @Test
    fun `with a screen reader the band asks for the tap instead of counting`() {
        showBoard(hold = BoardHold(remainingMs = 1_000, waitsForTap = true))
        say(PracticeStatus.Done(null))

        composeRule.onNodeWithTag("train.status").assertTextContains("Tap Next board when you are ready.", substring = true)
        composeRule.onNodeWithText("Next board in 1", substring = true).assertDoesNotExist()
    }

    /** Catches steps that count the wrong number of boards, or one that does not turn done when its board finishes. */
    @Test
    fun `the steps are one image that names the board and follow the chapter's length`() {
        showBoard(chapter = standInChapter(board = 3, boards = 4))
        composeRule.onNodeWithContentDescription("Board 3 of 4").assertIsDisplayed()
    }

    /** Catches a result page drawn under the title row or the board, or one with no way back on the phone. */
    @Test
    fun `the result page takes the whole pane and has its own back`() {
        show(PracticeState.Result(sampleResult()))

        composeRule.onNodeWithTag("train.result").assertIsDisplayed()
        composeRule.onNodeWithTag("train.count").assertDoesNotExist()
        composeRule.onNodeWithTag("train.back").performClick()
        assertEquals(listOf(0, 0, 1), listOf(tryAgain, tryAnother, closed))
    }

    /** Catches result buttons wired to one another. */
    @Test
    fun `each result button is its own action`() {
        show(PracticeState.Result(sampleResult()))
        composeRule.onNodeWithTag("train.result.next").performScrollTo().performClick()
        composeRule.onNodeWithTag("train.result.again").performScrollTo().performClick()
        composeRule.onNodeWithTag("train.result.back").performScrollTo().performClick()
        assertEquals(listOf(1, 1, 1), listOf(nextChapter, practiceAgain, closed))
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
        composeRule.onNodeWithText("Use that puzzle").performClick()
        assertEquals(listOf(0, 1, 0), listOf(tryAgain, tryAnother, closed))
    }

    @Test
    fun `the empty page goes back to the techniques`() {
        show(PracticeState.Empty)
        composeRule.onNodeWithText("Back to techniques").performClick()
        assertEquals(listOf(0, 0, 1), listOf(tryAgain, tryAnother, closed))
    }
}
