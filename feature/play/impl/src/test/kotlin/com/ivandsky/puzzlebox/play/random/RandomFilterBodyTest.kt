package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.play.domain.random.RandomPick
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Random body driven with fixed states (epic picker-filters, ticket 06): busy and the tablet's two panes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-420dpi")
class RandomFilterBodyTest {
    @get:Rule
    val rule = createComposeRule()

    private val games = listOf("alpha", "beta").map { RandomGame(GameId(it), it.replaceFirstChar(Char::uppercase), GameBrand.Neutral) }

    private fun stateOf(phase: RandomPhase, source: RandomSource = RandomSource.Community) =
        RandomPuzzleState(source, game = null, unsolvedOnly = true, difficulty = null, phase = phase)

    private fun show(state: () -> RandomPuzzleState, layout: PlayLayout = PlayLayout.SinglePane, onOpen: (RandomFilterPage) -> Unit = {}) =
        rule.setContent {
            PuzzleBoxTheme {
                RandomPuzzleContent(state(), games, layout, RandomPuzzleActions({}, {}, {}, {}, {}), onOpenFilter = onOpen)
            }
        }

    private fun node(tag: String) = rule.onNodeWithTag(tag)

    @Test
    fun `while a pick runs both chips, the toggle and the button are off, the button is the spinner, and they come back on Idle`() {
        val state = mutableStateOf(stateOf(RandomPhase.Picking))
        val tapped = mutableListOf<RandomFilterPage>()
        show({ state.value }, onOpen = { tapped += it })

        node("random.filter.game").assertIsNotEnabled().performClick()
        node("random.filter.difficulty").assertIsNotEnabled().performClick()
        node("random.source.past").assertIsNotEnabled()
        node("random.pick").assertIsNotEnabled().assertContentDescriptionEquals("Pick one for me")
        assertTrue(tapped.isEmpty())

        state.value = stateOf(RandomPhase.Idle)
        rule.waitForIdle()

        node("random.filter.game").assertIsEnabled()
        node("random.filter.difficulty").assertIsEnabled()
        node("random.pick").assertIsEnabled()
    }

    @Test
    fun `once a Past pick opened its puzzle the Game chip and the switch are off`() {
        val opened = RandomPhase.Opened(RandomPick.Day(GameId("alpha"), LocalDate.of(2026, 11, 10)))
        show({ stateOf(opened, RandomSource.Past) })

        node("random.filter.game").assertIsNotEnabled()
        node("random.unsolved").assertIsNotEnabled()
    }

    @Test
    fun `on a tablet the toggle and chips are left, the pick controls right and the panes 32dp apart`() {
        show({ stateOf(RandomPhase.Idle) }, PlayLayout.TwoPane)

        listOf("random.source.past", "random.filter.game", "random.filter.difficulty").forEach {
            rule.onNode(hasTestTag(it) and hasAnyAncestor(hasTestTag("random.left"))).assertExists()
        }
        rule.onNode(hasTestTag("random.pick") and hasAnyAncestor(hasTestTag("random.right"))).assertExists()
        val gap = node("random.right").getBoundsInRoot().left - node("random.left").getBoundsInRoot().right
        assertEquals(32f, gap.value, 0.5f)
    }
}
