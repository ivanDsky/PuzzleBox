package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.model.PlaySlot

/**
 * An 8×6 Strands board, "Autumn walk in the woods": seven theme words and a spangram the
 * friend drags letter to letter on the board, plus three dictionary words off the theme
 * ([NON_THEME]) the friend can tap in to earn a hint.
 */
object StrandsWoods : FriendGame("Strands") {
    const val BOARD = "strands.board"
    private const val ROWS = 8
    private const val COLS = 6

    private val LETTERS = listOf("FNGHCN", "SORIAB", "SHROWR", "OMUECT", "INSKSA", "PESELT", "ECEVAF", "NOROOL")

    private fun path(vararg cells: Pair<Int, Int>) = cells.map { (r, c) -> Coord(r, c) }

    private val THEME = listOf(
        "ACORN" to path(4 to 5, 3 to 4, 2 to 3, 1 to 2, 0 to 1),
        "BRANCH" to path(1 to 5, 2 to 5, 1 to 4, 0 to 5, 0 to 4, 0 to 3),
        "HUSK" to path(2 to 1, 3 to 2, 4 to 2, 4 to 3),
        "LEAVES" to path(5 to 4, 5 to 3, 6 to 4, 6 to 3, 6 to 2, 5 to 2),
        "MOSS" to path(3 to 1, 3 to 0, 2 to 0, 1 to 0),
        "PINECONE" to path(5 to 0, 4 to 0, 4 to 1, 5 to 1, 6 to 1, 7 to 1, 7 to 0, 6 to 0),
        "TWIG" to path(3 to 5, 2 to 4, 1 to 3, 0 to 2),
    )
    private val SPANGRAM = "FORESTFLOOR" to
        path(0 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 5, 7 to 5, 7 to 4, 7 to 3, 7 to 2)

    /** Dictionary words on the board, none crossing the theme words dragged afterwards. */
    val NON_THEME = listOf(
        path(1 to 4, 2 to 5, 3 to 5, 4 to 4),
        path(1 to 5, 2 to 5, 1 to 4, 0 to 5),
        path(0 to 4, 1 to 4, 2 to 5, 3 to 5),
    )

    override val board = EditorDraft(
        StrandsEditor,
        DraftState(
            StrandsDraft(
                letters = LETTERS.joinToString(""),
                clue = "Autumn walk in the woods",
                paths = (THEME + SPANGRAM).map { (word, cells) -> StrandsDraftPath(cells.map { it.row * COLS + it.col }, word) },
                spangram = THEME.size,
            ),
        ),
    )

    override fun ShellRobot.solve(slot: PlaySlot.Custom) {
        awaitBoard(slot)
        (THEME + SPANGRAM).forEach { (_, cells) -> drag(cells) }
    }

    /** [slot]'s controller once its session has loaded and the board shows. */
    fun ShellRobot.awaitBoard(slot: PlaySlot.Custom): StrandsPlayController =
        awaitController<StrandsPlayController>(slot) { it.session != null }.also { awaitTag(BOARD) }

    /** Taps each letter of [cells], then the last one again: the double-tap submit. */
    fun ShellRobot.tapWord(cells: List<Coord>) {
        (cells + cells.last()).forEach { at ->
            rule.onNodeWithTag("strands.cell.${at.row}.${at.col}").performScrollTo().performClick()
        }
        rule.waitForIdle()
    }

    private fun ShellRobot.drag(cells: List<Coord>) {
        rule.onNodeWithTag(BOARD).performScrollTo().performTouchInput {
            down(centreOf(cells.first()))
            cells.drop(1).forEach { moveTo(centreOf(it)) }
            up()
        }
        rule.waitForIdle()
    }

    private fun TouchInjectionScope.centreOf(at: Coord) =
        Offset(width * (at.col + 0.5f) / COLS, height * (at.row + 0.5f) / ROWS)
}
