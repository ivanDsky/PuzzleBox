package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayScreen
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chrome on the real play screen over the fixture board: both layouts put the theme card,
 * slot and counter in the board pane (A37), the hint control follows the economy (SC4), a
 * rejection reaches the slot, and a solve shows the rating word on a board that stays (A36).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h1000dp-420dpi")
class StrandsChromeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val solved = mutableListOf<Int>()

    private fun adopted() = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private fun show(controller: StrandsPlayController, layout: PlayLayout) = rule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            StrandsPlayScreen(
                controller = controller,
                status = PlayBoardStatus.Ready(banner = null),
                layout = layout,
                onRetry = {},
                onSolved = { _, hints -> solved += hints },
            )
        }
    }

    private fun StrandsPlayController.play(path: List<Coord>) {
        path.forEach(::tap)
        tap(path.last())
    }

    private fun assertChromeInBoardPane() {
        val inBoardPane = hasAnyAncestor(hasTestTag("strands.board_area"))
        for (tag in listOf("strands.theme_card", "strands.slot", "strands.counter", "strands.header_line")) {
            rule.onNode(hasTestTag(tag) and inBoardPane).assertExists()
        }
    }

    @Test
    fun `the phone column carries the theme card, slot and counter in the board pane`() {
        show(adopted(), PlayLayout.SinglePane)
        assertChromeInBoardPane()
        rule.onNodeWithTag("strands.hint").assertExists()
    }

    @Test
    fun `the tablet panes carry the theme card, slot and counter in the board pane`() {
        show(adopted(), PlayLayout.TwoPane)
        assertChromeInBoardPane()
        rule.onNode(hasTestTag("strands.hint") and hasAnyAncestor(hasTestTag("play.side_panel"))).assertExists()
    }

    @Test
    fun `three non-theme words fill the meter and enable the hint, whose press outlines a word`() {
        val controller = adopted()
        show(controller, PlayLayout.SinglePane)
        rule.onNodeWithTag("strands.hint").assertIsNotEnabled().assert(hasStateDescription(progress(0)))

        val words = StrandsUiFixtures.nonTheme.values.toList()
        controller.play(words[0])
        rule.onNodeWithTag("strands.hint").assertIsNotEnabled().assert(hasStateDescription(progress(1)))
        controller.play(words[1])
        controller.play(words[2])
        rule.onNodeWithTag("strands.hint").assertIsEnabled().assert(hasStateDescription("available"))

        rule.onNodeWithTag("strands.hint").performClick()
        assertEquals(HintStage.Outlined, controller.session?.state?.activeHint?.stage)
        rule.onNodeWithTag("strands.hint").assertIsNotEnabled()
    }

    @Test
    fun `a rejected trace shows its copy in the slot with the counter`() {
        val controller = adopted()
        show(controller, PlayLayout.SinglePane)
        controller.play(listOf(Coord(0, 0), Coord(0, 1)))
        rule.onNodeWithTag("strands.slot").assert(hasContentDescription("Too short. 0 of 4 theme words found"))
    }

    @Test
    fun `a solve shows the rating word and keeps the board on screen`() {
        val controller = adopted()
        show(controller, PlayLayout.SinglePane)
        (0 until 4).forEach { controller.play(StrandsUiFixtures.row(it)) }
        rule.waitForIdle()
        rule.onNodeWithTag("strands.theme_label").assertTextEquals("Perfect!")
        rule.onNodeWithTag("strands.counter").assertTextEquals("4 of 4 theme words found")
        rule.onNode(hasTestTag("strands.clue") and hasAnyAncestor(hasTestTag("strands.board_area"))).assertExists()
        assertEquals(listOf(0), solved)
    }

    private fun progress(thirds: Int) = "$thirds of 3 words toward the next hint"
}
