package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The word line a replay frame shows above the board: the submitted word read as live reads it, empty for any other step. */
class StrandsReplaySubmitSlotTest {

    private val start = StrandsState(StrandsUiFixtures.puzzle)

    @Test
    fun `the first frame and a hint show an empty line`() {
        assertEquals(WordSlot.Empty, submittedSlotOf(null, start))
        assertEquals(WordSlot.Empty, submittedSlotOf(start, start.copy(activeHint = ActiveHint("PIES", HintStage.Outlined))))
    }

    @Test
    fun `a theme word shows itself`() {
        assertEquals(
            WordSlot.Verdict("PIES", VerdictKind.Theme, seq = 1),
            submittedSlotOf(start, start.copy(found = listOf("PIES"))),
        )
    }

    @Test
    fun `the spangram reads as live reads it`() {
        val before = start.copy(found = listOf("PIES"))
        assertEquals(
            WordSlot.Verdict(StrandsCopy.SPANGRAM, VerdictKind.Spangram, seq = 2),
            submittedSlotOf(before, before.copy(found = listOf("PIES", "BAKE"))),
        )
    }

    @Test
    fun `a non-theme word shows the word though it marks no cell`() {
        val before = start.copy(found = listOf("PIES"), nonTheme = setOf("PIKE"))
        assertEquals(
            WordSlot.Verdict("RAKE", VerdictKind.NonTheme, seq = 3),
            submittedSlotOf(before, before.copy(nonTheme = before.nonTheme + "RAKE")),
        )
    }
}

/**
 * F16: the word line as the replay board draws it, above the board: a submitted word and its live verdict (read as live
 * reads it, then the counter), or an empty line of the same height, so the board never moves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsReplayWordLineTest {

    @get:Rule
    val rule = createComposeRule()

    private var frame by mutableStateOf(StrandsReplayScript.frameAfter(emptyList()))

    private fun show(shown: ReplayFrame) {
        frame = shown
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box { StrandsReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = true), Modifier.size(240.dp, 440.dp)) }
            }
        }
        rule.waitForIdle()
    }

    private fun showNext(next: ReplayFrame) {
        rule.runOnIdle { frame = next }
        rule.waitForIdle()
    }

    private val total = RealDay.themeWords.size + 1

    @Test
    fun `a theme word shows itself and the count`() {
        show(StrandsReplayScript.frameAfter(listOf(StrandsReplayScript.theme)))
        rule.onNodeWithTag("strands.slot").assertContentDescriptionEquals(StrandsCopy.announcement("ACORN", 1, total))
    }

    @Test
    fun `the spangram reads as live reads it`() {
        show(StrandsReplayScript.frameAfter(listOf(StrandsReplayScript.spangram)))
        rule.onNodeWithTag("strands.slot").assertContentDescriptionEquals(StrandsCopy.announcement(StrandsCopy.SPANGRAM, 1, total))
    }

    @Test
    fun `a non-theme word shows the word and the count though it marks no cell`() {
        show(StrandsReplayScript.frameAfter(listOf(StrandsReplayScript.theme, StrandsReplayScript.nonTheme)))
        rule.onNodeWithTag("strands.slot").assertContentDescriptionEquals(StrandsCopy.announcement("ARTS", 1, total))
    }

    @Test
    fun `with no submit the line is empty and the same height, so the board does not move`() {
        show(StrandsReplayScript.frameAfter(listOf(StrandsReplayScript.theme, StrandsReplayScript.nonTheme)))
        val slotHeight = rule.onNodeWithTag("strands.slot").getBoundsInRoot().height
        val boardTop = rule.onNodeWithTag("strands.board").getBoundsInRoot().top

        showNext(StrandsReplayScript.frameAfter(emptyList()))
        rule.onNodeWithTag("strands.slot").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        assertEquals(slotHeight, rule.onNodeWithTag("strands.slot").getBoundsInRoot().height)
        assertEquals(boardTop, rule.onNodeWithTag("strands.board").getBoundsInRoot().top)
    }
}
