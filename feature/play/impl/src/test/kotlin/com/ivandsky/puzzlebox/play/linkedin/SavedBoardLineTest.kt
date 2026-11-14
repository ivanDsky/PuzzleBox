package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.SavedBoardState
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun show(saved: SavedBoardState, firstRestore: Boolean = false) {
        current = saved
        restoring = firstRestore
        composeRule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.testTag("host")) { SavedBoardLine(current, zone = ZoneOffset.UTC, restoring = restoring) }
            }
        }
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
        show(SavedBoardState.NotYetRead, firstRestore = true)

        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("linkedin.saved_board.text", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSkeletonGivesWayToTheCurrentStatesWhenTheRestoreEnds() {
        show(SavedBoardState.NotYetRead, firstRestore = true)
        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertExists()

        composeRule.runOnIdle { restoring = false }
        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertDoesNotExist()
        linkedInNodes().assertCountEquals(0)

        composeRule.runOnIdle { restoring = true; current = SavedBoardState.Saved(readAtMs, final = true) }
        composeRule.onNodeWithTag("linkedin.saved_board.mark", useUnmergedTree = true).assertTextEquals("Final")
        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aPastWindowDayShowsTheSkeletonNotNotSavedWhileTheFirstRestoreRuns() {
        show(SavedBoardState.NeverSaved, firstRestore = true)

        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("linkedin.never_saved").assertDoesNotExist()
        composeRule.onNodeWithTag("linkedin.saved_board.text", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun afterTheRestoreANeverSavedDayShowsItsCopyVerbatim() {
        show(SavedBoardState.NeverSaved, firstRestore = true)
        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertExists()

        composeRule.runOnIdle { restoring = false }

        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("linkedin.saved_board.text", useUnmergedTree = true)
            .assertTextEquals("Not saved. Open a day within 7 days to keep its board.")
    }

    @Test
    fun aPastWindowDayThatTheRestoreBroughtABoardForShowsTheSavedLine() {
        show(SavedBoardState.NeverSaved, firstRestore = true)

        composeRule.runOnIdle { current = SavedBoardState.Saved(readAtMs, final = true); restoring = false }

        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("linkedin.saved_board.text", useUnmergedTree = true)
            .assertTextEquals("Saved board · as of Nov 4, 2:12 PM")
        composeRule.onNodeWithTag("linkedin.saved_board.mark", useUnmergedTree = true).assertTextEquals("Final")
    }

    @Test
    fun theSkeletonFadesOutIntoTheLineRatherThanSwappingAtOnce() {
        show(SavedBoardState.NeverSaved, firstRestore = true)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { restoring = false }
        val bothShown = frames { exists("linkedin.saved_board.restoring") && exists("linkedin.never_saved") }

        assertTrue("the skeleton and the line never showed together, so it swapped at once", bothShown.any { it })
        composeRule.onNodeWithTag("linkedin.saved_board.restoring", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("linkedin.never_saved").assertExists()
    }

    @Test
    fun aNotYetReadDayThatResolvesToNothingEasesItsHeightAwayInsteadOfCollapsing() {
        show(SavedBoardState.NotYetRead, firstRestore = true)
        val skeletonHeight = composeRule.onNodeWithTag("host").getBoundsInRoot().height
        assertTrue(skeletonHeight > 0.dp)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { restoring = false }
        val heights = frames { composeRule.onNodeWithTag("host").getBoundsInRoot().height }

        assertTrue("the height never showed a value between $skeletonHeight and 0: $heights", heights.any { it > 0.dp && it < skeletonHeight })
        assertEquals(0.dp, heights.last())
    }

    /** One sample of [read] per frame while the motion plays, ending after twice the medium duration. */
    private fun <T> frames(read: () -> T): List<T> {
        val samples = mutableListOf<T>()
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            samples += read()
        }
        return samples
    }

    private fun exists(tag: String) =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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
    fun aDayWithNoLineAndNoRestoreAddsNoGapInASpacedColumn() {
        composeRule.setContent {
            PuzzleBoxTheme {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth().height(10.dp).testTag("above"))
                    SavedBoardLine(SavedBoardState.NotYetRead, zone = ZoneOffset.UTC)
                    Box(Modifier.fillMaxWidth().height(10.dp).testTag("below"))
                }
            }
        }

        // 10dp of sibling and one 16dp gap: the empty line is no child at all, so there is no second gap.
        assertEquals(26.dp, composeRule.onNodeWithTag("below").getBoundsInRoot().top)
    }

    @Test
    fun theLineIsPureOverTheState() {
        assertNull(SavedBoardState.NotYetRead.toDayLine(ZoneOffset.UTC))
        assertEquals(DayLine.NeverSaved, SavedBoardState.NeverSaved.toDayLine(ZoneOffset.UTC))
        assertEquals(DayLine.Saved("Nov 4, 2:12 PM", final = true), SavedBoardState.Saved(readAtMs, true).toDayLine(ZoneOffset.UTC))
        // The device's zone only moves the clock, never the date's meaning.
        assertEquals("Nov 4, 9:12 AM", (SavedBoardState.Saved(readAtMs, false).toDayLine(ZoneOffset.ofHours(-5)) as DayLine.Saved).asOf)
    }

    private companion object {
        const val FRAME_MS = 16
        val FRAMES = 2 * MotionTokens.DurationMedium / FRAME_MS
    }
}
