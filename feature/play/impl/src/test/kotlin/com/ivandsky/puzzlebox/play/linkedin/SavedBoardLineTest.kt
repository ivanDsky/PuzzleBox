package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.SavedBoardState
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the day's LinkedIn board line says in each saved state (spec A36). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class SavedBoardLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var current by mutableStateOf<SavedBoardState>(SavedBoardState.NotYetRead)

    // 2026-11-04 14:12 UTC.
    private val readAtMs = 1_793_801_520_000L

    private var restoring by mutableStateOf(false)

    private fun show(saved: SavedBoardState, restoring: Boolean = false) {
        current = saved
        this.restoring = restoring
        composeRule.setContent { PuzzleBoxTheme { SavedBoardLine(current, zone = ZoneOffset.UTC, restoring = this.restoring) } }
    }

    private fun linkedInNodes() = composeRule.onAllNodes(
        SemanticsMatcher("a linkedin node") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("linkedin.") == true },
        useUnmergedTree = true,
    )

    @Test
    fun aFinalBoardSaysWhenItWasReadAndThatItIsFinal() {
        show(SavedBoardState.Saved(readAtMs, final = true))

        composeRule.onNodeWithTag("linkedin.saved_board.text", useUnmergedTree = true)
            .assertTextEquals("Saved board · as of Nov 4, 2:12 PM")
        composeRule.onNodeWithTag("linkedin.saved_board.mark", useUnmergedTree = true).assertTextEquals("Final")
        composeRule.onNodeWithTag("linkedin.saved_board").assertContentDescriptionEquals("Saved board as of Nov 4, 2:12 PM, final")
        composeRule.onNodeWithTag("linkedin.never_saved").assertDoesNotExist()
    }

    @Test
    fun aPartialBoardSaysItIsPartial() {
        show(SavedBoardState.Saved(readAtMs, final = false))

        composeRule.onNodeWithTag("linkedin.saved_board.mark", useUnmergedTree = true).assertTextEquals("Partial")
        composeRule.onNodeWithTag("linkedin.saved_board").assertContentDescriptionEquals("Saved board as of Nov 4, 2:12 PM, partial")
    }

    @Test
    fun theSavedTimeIsFixedAndNeverARelativeAge() {
        show(SavedBoardState.Saved(readAtMs, final = true))
        val text = composeRule.onNodeWithTag("linkedin.saved_board.text", useUnmergedTree = true)
        val before = text.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

        composeRule.mainClock.advanceTimeBy(60 * 60 * 1000L)
        composeRule.waitForIdle()

        assertEquals(before, text.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text })
        assertEquals(false, before.orEmpty().contains("ago"))
    }

    @Test
    fun aNeverSavedDayTellsHowToKeepABoard() {
        show(SavedBoardState.NeverSaved)

        composeRule.onNodeWithTag("linkedin.saved_board.text", useUnmergedTree = true)
            .assertTextEquals("Not saved. Open a day within 7 days to keep its board.")
        composeRule.onNodeWithTag("linkedin.never_saved")
            .assertContentDescriptionEquals("Not saved. Open a day within 7 days to keep its board.")
        composeRule.onNodeWithTag("linkedin.saved_board.mark", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("linkedin.saved_board").assertDoesNotExist()
    }

    @Test
    fun aDayNotYetReadDrawsNoLine() {
        show(SavedBoardState.NotYetRead)

        linkedInNodes().assertCountEquals(0)
    }

    @Test
    fun aDayWithNoBoardYetShowsASkeletonWhileTheFirstRestoreRuns() {
        show(SavedBoardState.NotYetRead, restoring = true)

        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("linkedin.saved_board.text", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSkeletonGivesWayToTheCurrentStatesWhenTheRestoreEnds() {
        show(SavedBoardState.NotYetRead, restoring = true)
        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertExists()

        composeRule.runOnIdle { restoring = false }
        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertDoesNotExist()
        linkedInNodes().assertCountEquals(0)

        composeRule.runOnIdle { restoring = true; current = SavedBoardState.Saved(readAtMs, final = true) }
        composeRule.onNodeWithTag("linkedin.saved_board.mark", useUnmergedTree = true).assertTextEquals("Final")
        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theLineFollowsTheStateAsABoardIsSaved() {
        show(SavedBoardState.NotYetRead)
        linkedInNodes().assertCountEquals(0)

        composeRule.runOnIdle { current = SavedBoardState.Saved(readAtMs, final = false) }
        composeRule.onNodeWithTag("linkedin.saved_board.mark", useUnmergedTree = true).assertTextEquals("Partial")

        composeRule.runOnIdle { current = SavedBoardState.Saved(readAtMs, final = true) }
        composeRule.onNodeWithTag("linkedin.saved_board.mark", useUnmergedTree = true).assertTextEquals("Final")
    }

    @Test
    fun theLineIsPureOverTheState() {
        assertNull(SavedBoardState.NotYetRead.toDayLine(ZoneOffset.UTC))
        assertEquals(DayLine.NeverSaved, SavedBoardState.NeverSaved.toDayLine(ZoneOffset.UTC))
        assertEquals(DayLine.Saved("Nov 4, 2:12 PM", final = true), SavedBoardState.Saved(readAtMs, true).toDayLine(ZoneOffset.UTC))
        // The device's zone only moves the clock, never the date's meaning.
        assertEquals("Nov 4, 9:12 AM", (SavedBoardState.Saved(readAtMs, false).toDayLine(ZoneOffset.ofHours(-5)) as DayLine.Saved).asOf)
    }
}
