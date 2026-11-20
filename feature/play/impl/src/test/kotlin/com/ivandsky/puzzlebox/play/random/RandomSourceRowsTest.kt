package com.ivandsky.puzzlebox.play.random

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Random body's source choice behind the ChoiceRows flag: radio rows on, the segmented control off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h900dp-420dpi")
class RandomSourceRowsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun stateOf(
        phase: RandomPhase = RandomPhase.Idle,
        source: RandomSource = RandomSource.Past,
        choiceRows: Boolean = true,
    ) = RandomPuzzleState(source, game = null, unsolvedOnly = true, difficulty = null, phase = phase, choiceRows = choiceRows)

    private fun show(state: RandomPuzzleState, onSource: (RandomSource) -> Unit = {}, layout: PlayLayout = PlayLayout.SinglePane) =
        rule.setContent {
            PuzzleBoxTheme { RandomPuzzleContent(state, emptyList(), layout, RandomPuzzleActions(onSource, {}, {}, {}, {})) }
        }

    private fun node(tag: String) = rule.onNodeWithTag(tag)

    @Test
    fun `flag on shows both rows with their lines and the chosen one selected`() {
        show(stateOf(source = RandomSource.Community))

        rule.onNode(hasText("Ones you can still play from the archive")).assertExists()
        rule.onNode(hasText("Shared by other people today")).assertExists()
        node("random.source.community").assertIsSelected()
    }

    @Test
    fun `flag off keeps the segmented control with no lines`() {
        show(stateOf(choiceRows = false))

        node("random.source.past").assertIsSelected()
        node("random.source.community").assertExists()
        rule.onNode(hasText("Shared by other people today")).assertDoesNotExist()
    }

    @Test
    fun `tapping a row reports its source and a Past row keeps Not solved yet`() {
        val chosen = mutableListOf<RandomSource>()
        show(stateOf(), onSource = { chosen += it })

        node("random.unsolved").assertExists()
        node("random.source.community").performClick()

        assertEquals(listOf(RandomSource.Community), chosen)
    }

    @Test
    fun `while a pick runs both rows are off and the chosen one says Picking a puzzle`() {
        show(stateOf(phase = RandomPhase.Picking, source = RandomSource.Community))

        node("random.source.past").assertIsNotEnabled()
        node("random.source.community").assertIsNotEnabled()
        rule.onNode(hasText("Picking a puzzle…")).assertExists()
        rule.onNode(hasText("Shared by other people today")).assertDoesNotExist()
        rule.onNode(hasText("Ones you can still play from the archive")).assertExists()
    }

    @Test
    fun `an idle sheet has both rows on`() {
        show(stateOf())

        node("random.source.past").assertIsEnabled()
        node("random.source.community").assertIsEnabled()
    }

    @Test
    fun `a failed pick rings the rows but finding nothing does not`() {
        val failures = RandomMessage.entries.filter { it.isFailure() }.toSet()

        assertEquals(setOf(RandomMessage.Offline, RandomMessage.Throttled, RandomMessage.NoneOpened, RandomMessage.Refused), failures)
    }
}
