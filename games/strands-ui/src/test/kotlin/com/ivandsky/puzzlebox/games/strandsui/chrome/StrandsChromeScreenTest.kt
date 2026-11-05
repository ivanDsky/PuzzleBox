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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayScreen
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strandsui.HintDialog
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.WordSlot
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `three non-theme words fill the meter and make a hint available, whose press outlines a word`() {
        val controller = adopted()
        show(controller, PlayLayout.SinglePane)
        rule.onNodeWithTag("strands.hint").assert(hasStateDescription(unavailable(0)))

        val words = StrandsUiFixtures.nonTheme.values.toList()
        controller.play(words[0])
        rule.onNodeWithTag("strands.hint").assert(hasStateDescription(unavailable(1)))
        controller.play(words[1])
        controller.play(words[2])
        rule.onNodeWithTag("strands.hint").assertIsEnabled().assert(hasStateDescription("available"))

        rule.onNodeWithTag("strands.hint").performClick()
        assertEquals(HintStage.Outlined, controller.session?.state?.activeHint?.stage)
        assertEquals(1, controller.session?.state?.hintsUsed)
        rule.onNodeWithTag("strands.hint").assert(hasStateDescription(unavailable(0)))
    }

    @Test
    fun `pressing with no hint available opens Need a hint and spends nothing`() {
        val controller = adopted()
        show(controller, PlayLayout.SinglePane)
        rule.onNodeWithTag("strands.hint").assertIsEnabled().performClick()

        assertEquals(HintDialog.NoneAvailable, controller.hintDialog)
        rule.onNodeWithText(StrandsCopy.HINT_NONE_AVAILABLE_TITLE).assertExists()
        assertEquals(0, controller.session?.state?.hintsUsed)
        assertNull(controller.session?.state?.activeHint)

        rule.onNodeWithTag("strands.hint_dialog_dismiss").performClick()
        rule.onNodeWithTag("strands.hint_dialog").assertDoesNotExist()
        assertEquals(0, controller.session?.state?.hintsUsed)
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

    @Test
    fun `a tap off the board drops the trace, a tap on the board extends it`() {
        val controller = adopted()
        show(controller, PlayLayout.SinglePane)
        rule.onNodeWithTag("strands.cell.0.0").performClick()
        rule.onNodeWithTag("strands.cell.0.1").performClick()
        assertEquals(listOf(Coord(0, 0), Coord(0, 1)), controller.trace.path)

        rule.onNodeWithTag("strands.theme_card").performClick()
        assertTrue(controller.trace.path.isEmpty())
        assertEquals(WordSlot.Empty, controller.slot)
    }

    @Test
    fun `a tap off the board leaves a shown verdict alone when nothing is traced`() {
        val controller = adopted()
        show(controller, PlayLayout.SinglePane)
        controller.play(listOf(Coord(0, 0), Coord(0, 1)))
        val verdict = controller.slot
        rule.onNodeWithTag("strands.counter").performClick()
        assertEquals(verdict, controller.slot)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-420dpi")
    fun `on a landscape phone the 8-row board keeps 48 dp cells and scrolls into view`() {
        val controller = StrandsPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}))
        assertEquals(Adopt.Adopted, controller.adoptToday(StrandsCodec.envelope(tallPuzzle), StrandsUiFixtures.ref))
        show(controller, PlayLayout.SinglePane)

        rule.onNodeWithTag("strands.scroll").assert(hasScrollAction())
        rule.onNodeWithTag("strands.board").assertHeightIsAtLeast(48.dp * 8)
        rule.onNodeWithTag("strands.cell.7.5").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("strands.cell.7.5").performClick()
        assertEquals(listOf(Coord(7, 5)), controller.trace.path)
        rule.onNodeWithTag("strands.hint").performScrollTo().assertIsDisplayed()
    }

    private fun unavailable(thirds: Int) = "No hint available, $thirds of 3 words toward the next hint"

    /** NYT's 8 × 6 board: each row a theme word, the last the spangram (edge to edge). */
    private val tallPuzzle: StrandsPuzzle = run {
        val rows = listOf("PLANET", "ORANGE", "BANANA", "CHERRY", "GARDEN", "SUMMER", "WINTER", "SPRING")
        val words = rows.mapIndexed { r, text -> StrandsWord(text, (0 until 6).map { Coord(r, it) }) }
        StrandsPuzzle(
            rows = 8,
            cols = 6,
            letters = rows.joinToString(""),
            clue = "Tall",
            spangram = words.last(),
            themeWords = words.dropLast(1),
            validWords = rows.toSet(),
            constructors = "Tess Tester",
        )
    }
}
