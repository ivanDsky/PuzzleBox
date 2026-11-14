package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Two boards drawn as one list, as far from the one to the other as the scrub says: a row on both is between its two
 * places and rolls its place, a row on one only stays put and frosts out early or in late, and at either end the list
 * is that end's board.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScrubbedBoardListTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Cal climbs from third to first and Ann drops to second; Bea is only on the board being left, Dee and Eve only
    // on the arriving one, which is a row taller.
    private val leaving = face("Ann", "Bea", "Cal")
    private val arriving = face("Cal", "Ann", "Dee", "Eve")

    private val progress = mutableFloatStateOf(0f)
    private var atRest by mutableStateOf<BoardFace?>(null)

    private fun face(vararg names: String) = BoardFace(
        "Zip",
        RankOrder.TimeThenHints,
        DailyBoardState.Ranked(names.mapIndexed { index, name -> DailyBoardRowModel(index + 1, name, 60_000L, 0, isSelf = false, playerId = name) }),
    )

    /** The list between [leaving] and [arriving]; a board set [atRest] is drawn in its place, as the hub draws one board. */
    private fun show() {
        val parts = MorphParts(BoardMorph(leaving, arriving) { progress.floatValue }, isToday = true)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val board = atRest
                if (board == null) {
                    ScrubbedBoardList(parts, medals = false, backdrop = MaterialTheme.colorScheme.background)
                } else {
                    DailyBoardList(
                        boardLines(board.state, board.ghosts, board.order),
                        board.order,
                        style = DailyBoardStyle.defaults(motion = DailyBoardStyle.Motion.Animated),
                    )
                }
            }
        }
    }

    private fun row(name: String) = hasTestTag("daily_board.row") and hasAnyDescendant(hasText(name))

    // Between two boards the list says nothing to assistive tech, so its rows are only in the unmerged tree.
    private fun bounds(matcher: SemanticsMatcher) = composeRule.onNode(matcher, useUnmergedTree = true).getBoundsInRoot()

    private fun top(name: String): Int = bounds(row(name)).top.dp()

    private fun shown(name: String): Float = composeRule.onNode(row(name), useUnmergedTree = true).opacity()

    private fun listHeight(): Int = bounds(hasTestTag("daily_board.list")).let { it.bottom - it.top }.dp()

    // The list's bottom edge, then the top and bottom edge of each of [names]' rows, in dp.
    private fun layout(names: List<String>): List<Int> =
        listOf(bounds(hasTestTag("daily_board.list")).bottom.dp()) + names.flatMap { name -> bounds(row(name)).let { listOf(it.top.dp(), it.bottom.dp()) } }

    private fun Dp.dp(): Int = value.roundToInt()

    // A row is 64 dp and a gap 8 dp: the third row's top is 144 dp down, the first one's at 0.
    @Test
    fun `a row on both boards is between its two places as far as the scrub has gone`() {
        show()

        assertEquals(listOf(144, 108, 72, 0), progress.at(0f, 0.25f, 0.5f, 1f) { top("Cal") })
    }

    @Test
    fun `a row on both boards never frosts`() {
        show()

        assertEquals(listOf(1f, 1f, 1f), progress.at(0f, 0.5f, 1f) { shown("Cal") })
    }

    @Test
    fun `a row only the board being left has stays where that board has it`() {
        show()

        assertEquals(listOf(72, 72, 72), progress.at(0f, 0.25f, 0.5f) { top("Bea") })
    }

    @Test
    fun `a row only the board being left has frosts out over the first half of the way`() {
        show()

        assertEquals(listOf(1f, 0.5f, 0f, 0f), progress.at(0f, 0.25f, 0.5f, 1f) { shown("Bea") })
    }

    @Test
    fun `a row only the arriving board has waits where that board has it`() {
        show()

        assertEquals(listOf(216, 216, 216), progress.at(0.5f, 0.75f, 1f) { top("Eve") })
    }

    @Test
    fun `a row only the arriving board has frosts in over the second half of the way`() {
        show()

        assertEquals(listOf(0f, 0f, 0.5f, 1f), progress.at(0f, 0.5f, 0.75f, 1f) { shown("Eve") })
    }

    // Three rows are 208 dp and four 280 dp.
    @Test
    fun `between boards of two heights the list is as tall as the scrub has gone`() {
        show()

        assertEquals(listOf(208, 226, 244, 280), progress.at(0f, 0.25f, 0.5f, 1f) { listHeight() })
    }

    @Test
    fun `a scrub that has not started draws the board being left`() {
        val names = listOf("Ann", "Bea", "Cal")
        show()

        val scrubbed = layout(names)
        val notYet = listOf(shown("Dee"), shown("Eve"))
        atRest = leaving

        assertEquals(layout(names), scrubbed)
        assertEquals(listOf(0f, 0f), notYet)
    }

    @Test
    fun `a finished scrub draws the arriving board`() {
        val names = listOf("Cal", "Ann", "Dee", "Eve")
        show()

        progress.floatValue = 1f
        val scrubbed = layout(names)
        val gone = shown("Bea")
        atRest = arriving

        assertEquals(layout(names), scrubbed)
        assertEquals(0f, gone, 0f)
    }

    // Cal goes from place 3 to place 1: its cell rolls past two numbers of 28 dp, the old one down and out.
    @Test
    fun `the place of a row on its way rolls a number for every place it passes, in step with the scrub`() {
        val cell = hasTestTag("daily_board.row.rank") and hasAnyAncestor(row("Cal"))
        show()

        assertEquals("the old place", listOf(0, 14, 28, 56), progress.at(0f, 0.25f, 0.5f, 1f) { composeRule.rolledBelow(cell, place = 3) })
        assertEquals("the place between", listOf(-28, 0, 28), progress.at(0f, 0.5f, 1f) { composeRule.rolledBelow(cell, place = 2) })
        assertEquals("the new place", listOf(-56, -28, 0), progress.at(0f, 0.5f, 1f) { composeRule.rolledBelow(cell, place = 1) })
    }
}

/** What [read] finds at each of [stops] of the way, in turn, when this is the scrub's progress. */
internal fun <T> MutableFloatState.at(vararg stops: Float, read: () -> T): List<T> = stops.map { stop ->
    floatValue = stop
    read()
}

/**
 * How much of this node shows: the opacities of the layers it is drawn in, multiplied as they are on screen. Opacity
 * is a layer property no semantics reports, and a host test has no pixels to read it from.
 */
internal fun SemanticsNodeInteraction.opacity(): Float {
    var at: LayoutInfo? = fetchSemanticsNode().layoutInfo
    var opacity = 1f
    while (at != null) {
        for (info in at.getModifierInfo()) opacity *= layerAlpha(info.modifier)
        at = at.parentInfo
    }
    return opacity
}

private fun layerAlpha(modifier: Modifier): Float {
    val layer = (modifier as? InspectableValue)?.takeIf { it.nameFallback == "graphicsLayer" } ?: return 1f
    val block = layer.inspectableElements.firstOrNull { it.name == "block" }?.value ?: return 1f
    @Suppress("UNCHECKED_CAST")
    return GraphicsLayerScope().apply(block as GraphicsLayerScope.() -> Unit).alpha
}

/**
 * How far below the middle of the place cell [cell] the number [place] of its roll is drawn, in dp: 0 is the number
 * the cell shows. The cell clips the others, so they are read where they are drawn and not where they are seen.
 */
internal fun SemanticsNodeInteractionsProvider.rolledBelow(cell: SemanticsMatcher, place: Int): Int {
    val window = onNode(cell, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val number = onNode(hasText("$place") and hasAnyAncestor(cell), useUnmergedTree = true).getUnclippedBoundsInRoot()
    return ((number.top + number.bottom - window.top - window.bottom) / 2).value.roundToInt()
}
