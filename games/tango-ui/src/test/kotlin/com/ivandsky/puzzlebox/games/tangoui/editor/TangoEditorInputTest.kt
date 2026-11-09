package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tango.editor.cycleMarker
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridEdge
import com.ivandsky.puzzlebox.gameui.GridGeometry
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Preview on press, commit on lift, cancel on drag-off; a square or an edge under the finger. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class TangoEditorInputTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()
    private val controller =
        TangoEditorController(EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())) { edits += it }

    private fun board(): TangoDraft = controller.draft.value.state.draft as TangoDraft

    private fun show() {
        rule.setContent { PuzzleBoxTheme { TangoEditorBoard(controller, Modifier.fillMaxWidth()) } }
    }

    private fun TouchInjectionScope.centre(cell: Int, n: Int = N) = Offset(
        x = (cell % n + 0.5f) * width / n,
        y = (cell / n + 0.5f) * height / n,
    )

    private fun TouchInjectionScope.rightEdge(cell: Int, n: Int = N) = centre(cell, n) + Offset(width / n / 2f, 0f)

    private fun TouchInjectionScope.bottomEdge(cell: Int) = centre(cell) + Offset(0f, height / N / 2f)

    private fun squareTag(cell: Int) = "tango.editor.cell.$cell.preview"

    private fun edgeTag(cell: Int, side: String) = "tango.editor.edge.$cell.$side"

    @Test
    fun `pressing a square previews it and lifting commits one sun`() {
        show()

        rule.onNodeWithTag(BOARD).performTouchInput { down(centre(5)) }

        rule.onAllNodesWithTag(squareTag(5), useUnmergedTree = true).assertCountEquals(1)
        assertTrue(edits.isEmpty())

        rule.onNodeWithTag(BOARD).performTouchInput { up() }

        rule.onAllNodesWithTag(squareTag(5), useUnmergedTree = true).assertCountEquals(0)
        assertEquals(mapOf(5 to TangoCell.SUN), board().givens)
        assertEquals(1, edits.size)
    }

    @Test
    fun `dragging off a square cancels without an edit`() {
        show()

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(centre(5))
            moveTo(centre(5) + Offset(width / N * 2f, 0f))
        }
        rule.onAllNodesWithTag(squareTag(5), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }

        assertTrue(board().givens.isEmpty())
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `a move under the touch slop still commits`() {
        show()

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(centre(5))
            moveTo(centre(5) + Offset(viewConfiguration.touchSlop - 2f, 0f))
            up()
        }

        assertEquals(mapOf(5 to TangoCell.SUN), board().givens)
    }

    @Test
    fun `one move just past the touch slop inside the cell cancels`() {
        show()

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(centre(5))
            moveTo(centre(5) + Offset(viewConfiguration.touchSlop + 2f, 0f))
        }
        rule.onAllNodesWithTag(squareTag(5), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }

        assertTrue(edits.isEmpty())
    }

    @Test
    fun `after the board is resized taps still hit the right edge and cell`() {
        val boardWidth = mutableStateOf(300.dp)
        rule.setContent { PuzzleBoxTheme { TangoEditorBoard(controller, Modifier.width(boardWidth.value)) } }
        rule.onNodeWithTag(BOARD).performTouchInput { down(centre(0)); up() }
        boardWidth.value = 420.dp
        rule.waitForIdle()

        rule.onNodeWithTag(BOARD).performTouchInput { down(rightEdge(8)) }
        rule.onAllNodesWithTag(edgeTag(8, "right"), useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
        rule.onNodeWithTag(BOARD).performTouchInput { down(centre(35)) }
        rule.onAllNodesWithTag(squareTag(35), useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }

        assertEquals(mapOf(0 to TangoCell.SUN, 35 to TangoCell.SUN), board().givens)
        assertEquals(listOf(TangoMarker(8, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)), board().markers)
    }

    @Test
    fun `a tap near a line's middle edits the right edge marker`() {
        show()

        rule.onNodeWithTag(BOARD).performTouchInput { down(rightEdge(8)) }
        rule.onAllNodesWithTag(edgeTag(8, "right"), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag(squareTag(5), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }

        assertEquals(listOf(TangoMarker(8, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)), board().markers)
        assertTrue(board().givens.isEmpty())
    }

    @Test
    fun `a tap near a bottom line's middle edits the bottom edge marker`() {
        show()

        rule.onNodeWithTag(BOARD).performTouchInput { down(bottomEdge(5)) }
        rule.onAllNodesWithTag(edgeTag(5, "bottom"), useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }

        assertEquals(listOf(TangoMarker(5, TangoEdgeSide.DOWN, TangoMarkerKind.SAME)), board().markers)
    }

    @Test
    fun `a tap near a corner edits the cell not the marker`() {
        show()

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(centre(5) + Offset(width / N * 0.45f, height / N * 0.45f))
            up()
        }

        assertEquals(mapOf(5 to TangoCell.SUN), board().givens)
        assertTrue(board().markers.isEmpty())
    }

    @Test
    fun `release without a press is no edit`() {
        controller.release()

        assertTrue(edits.isEmpty())
    }

    @Test
    fun `cancelling a press clears the preview and edits nothing`() {
        controller.press(TangoEditorTarget.Square(3))
        assertEquals(TangoEditorTarget.Square(3), controller.preview.value)

        controller.cancelPress()
        controller.release()

        assertNull(controller.preview.value)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `edge taps keep markers canonical and every step reverts`() {
        val edges = listOf(
            GridEdge(GridCell(2, 1), GridEdge.Side.Bottom),
            GridEdge(GridCell(0, 3), GridEdge.Side.Right),
            GridEdge(GridCell(2, 1), GridEdge.Side.Bottom),
            GridEdge(GridCell(0, 0), GridEdge.Side.Right),
            GridEdge(GridCell(0, 3), GridEdge.Side.Right),
            GridEdge(GridCell(0, 3), GridEdge.Side.Right),
        )
        val trail = ArrayList<TangoDraft>()
        edges.forEach {
            val before = board()
            val command = before.cycleMarker(
                it.cell.row * N + it.cell.col,
                if (it.side == GridEdge.Side.Right) TangoEdgeSide.RIGHT else TangoEdgeSide.DOWN,
            )
            assertEquals(before, command.revert(command.apply(before)))
            trail += before
            controller.press(TangoEditorTarget.Edge(it))
            controller.release()

            val markers = board().markers
            assertEquals(markers.sortedWith(compareBy({ m -> m.cell }, { m -> m.side.ordinal })), markers)
            assertEquals(markers.size, markers.map { m -> m.cell to m.side }.toSet().size)
            assertEquals(command.apply(before), board())
        }

        var undone = controller.draft.value
        for (expected in trail.asReversed()) {
            undone = checkNotNull(undone.undo())
            assertEquals(expected, undone.state.draft)
        }
    }

    @Test
    fun `on a 328dp phone board at n10 a centre, a shared edge and a corner each hit their own target`() {
        phoneTaps(n = 10, cell = 44)
    }

    @Test
    fun `on a 328dp phone board at n4 a centre, a shared edge and a corner each hit their own target`() {
        phoneTaps(n = 4, cell = 5)
    }

    @Test
    fun `on a 328dp phone board at n8 a centre, a shared edge and a corner each hit their own target`() {
        phoneTaps(n = 8, cell = 27)
    }

    @Test
    fun `at n10 on a 328dp board the edge band is at least 14dp and the cell centre at least 18dp`() {
        val pitch = PHONE_BOARD / 10
        val band = pitch * (2 * TangoBoardSpec.editEdgeBandAcrossFraction)
        val centre = pitch * (1 - 2 * TangoBoardSpec.editEdgeBandAcrossFraction)

        assertTrue("band was $band", band >= 14.dp)
        assertTrue("centre was $centre", centre >= 18.dp)
    }

    @Test
    fun `at n4, n8 and n10 every point inside a drawn edge band hits that edge and every other point its cell`() {
        val side = PHONE_BOARD.value
        val steps = 400
        for (n in listOf(4, 8, 10)) {
            val geometry = GridGeometry(n, n, side, side)
            val unit = side / n
            val hit = HashSet<GridEdge>()
            for (i in 0 until steps) {
                for (j in 0 until steps) {
                    val u = (i + 0.5f) * n / steps
                    val v = (j + 0.5f) * n / steps
                    val inside = drawnBandsAt(u, v, n, grow = -SLACK)
                    if (inside != drawnBandsAt(u, v, n, grow = SLACK)) continue // on a band outline
                    val edge = geometry.edgeAt(Offset(u * unit, v * unit), ACROSS, INSET)
                    assertEquals("n=$n at ($u, $v)", inside.singleOrNull(), edge)
                    edge?.let(hit::add)
                }
            }
            assertEquals("n=$n bands hit", interiorEdges(n), hit)
        }
    }

    @Test
    fun `on a 4x4 board a press inside each drawn band previews its edge and just outside its square`() {
        pressProbes(n = 4) { true }
    }

    @Test
    fun `on an 8x8 board a press inside a drawn band previews its edge and just outside its square`() {
        pressProbes(n = 8) { it.cell.row == it.cell.col }
    }

    @Test
    fun `on a 10x10 board a press inside a drawn band previews its edge and just outside its square`() {
        pressProbes(n = 10) { it.cell.row == it.cell.col }
    }

    /**
     * Presses the 328 dp board of width [n] inside each [chosen] edge's drawn band (centre and the
     * four corners just inside) and just outside it (past the inset, past the band edge), and reads
     * the controller's preview: the edge inside, the square under the finger outside.
     */
    private fun pressProbes(n: Int, chosen: (GridEdge) -> Boolean) {
        val sized = TangoEditorController(EditorDraft(TangoEditor, DraftState(TangoDraft(n, emptyMap(), emptyList())))) {}
        rule.setContent { PuzzleBoxTheme { TangoEditorBoard(sized, Modifier.width(PHONE_BOARD)) } }
        fun pressAt(u: Float, v: Float): TangoEditorTarget? {
            rule.onNodeWithTag(BOARD).performTouchInput { down(Offset(u * width / n, v * height / n)) }
            val target = sized.preview.value
            sized.cancelPress()
            rule.onNodeWithTag(BOARD).performTouchInput { up() }
            return target
        }
        fun square(u: Float, v: Float) = TangoEditorTarget.Square(v.toInt() * n + u.toInt())
        val edges = interiorEdges(n).filter(chosen)
        assertTrue("no edges probed at n=$n", edges.isNotEmpty())
        edges.forEach { edge ->
            val band = bandRect(edge)
            val near = band.deflate(PROBE)
            listOf(near.center, near.topLeft, near.topRight, near.bottomLeft, near.bottomRight).forEach { p ->
                assertEquals("n=$n $edge at $p", TangoEditorTarget.Edge(edge), pressAt(p.x, p.y))
            }
            val outside = when (edge.side) {
                // Past the inset (along the line, a hair into the upper/left cell) and past the band edge.
                GridEdge.Side.Right -> listOf(Offset(band.center.x - PROBE, band.top - PROBE), Offset(band.left - PROBE, band.center.y))
                GridEdge.Side.Bottom -> listOf(Offset(band.left - PROBE, band.center.y - PROBE), Offset(band.center.x, band.top - PROBE))
            }
            outside.forEach { p -> assertEquals("n=$n off $edge at $p", square(p.x, p.y), pressAt(p.x, p.y)) }
        }
    }

    /** Interior edges of an n×n board: each cell's right edge but the last column's, bottom edge but the last row's. */
    private fun interiorEdges(n: Int): Set<GridEdge> = (0 until n).flatMap { row ->
        (0 until n).flatMap { col ->
            listOfNotNull(
                GridEdge(GridCell(row, col), GridEdge.Side.Right).takeIf { col < n - 1 },
                GridEdge(GridCell(row, col), GridEdge.Side.Bottom).takeIf { row < n - 1 },
            )
        }
    }.toSet()

    /** The band rect `TapZoneBands`/`PressWash` draw for [edge], in cell units. */
    private fun bandRect(edge: GridEdge): Rect {
        val row = edge.cell.row.toFloat()
        val col = edge.cell.col.toFloat()
        return when (edge.side) {
            GridEdge.Side.Right -> Rect(Offset(col + 1 - ACROSS, row + INSET), Size(2 * ACROSS, 1 - 2 * INSET))
            GridEdge.Side.Bottom -> Rect(Offset(col + INSET, row + 1 - ACROSS), Size(1 - 2 * INSET, 2 * ACROSS))
        }
    }

    /** The interior edges whose drawn band, grown by [grow] cell units, holds (u, v); only the four nearest cells' edges can. */
    private fun drawnBandsAt(u: Float, v: Float, n: Int, grow: Float): Set<GridEdge> {
        val near = (v.toInt() - 1..v.toInt()).flatMap { row ->
            (u.toInt() - 1..u.toInt()).filter { row in 0 until n && it in 0 until n }.flatMap { col ->
                listOf(GridEdge(GridCell(row, col), GridEdge.Side.Right), GridEdge(GridCell(row, col), GridEdge.Side.Bottom))
            }
        }
        return near.filter { edge ->
            val interior = if (edge.side == GridEdge.Side.Right) edge.cell.col < n - 1 else edge.cell.row < n - 1
            interior && bandRect(edge).inflate(grow).contains(Offset(u, v))
        }.toSet()
    }

    /** A press shows the target's wash, a lift commits it: centre → sun, shared right edge → `=`, corner → moon. */
    private fun phoneTaps(n: Int, cell: Int) {
        val sized = TangoEditorController(EditorDraft(TangoEditor, DraftState(TangoDraft(n, emptyMap(), emptyList())))) {
            edits += it
        }
        fun board() = sized.draft.value.state.draft as TangoDraft
        rule.setContent { PuzzleBoxTheme { TangoEditorBoard(sized, Modifier.width(PHONE_BOARD)) } }
        rule.onNodeWithTag(BOARD).assertWidthIsEqualTo(PHONE_BOARD).assertHeightIsEqualTo(PHONE_BOARD)

        rule.onNodeWithTag(BOARD).performTouchInput { down(centre(cell, n)) }
        rule.onAllNodesWithTag(squareTag(cell), useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
        assertEquals(mapOf(cell to TangoCell.SUN), board().givens)

        rule.onNodeWithTag(BOARD).performTouchInput { down(rightEdge(cell, n)) }
        rule.onAllNodesWithTag(edgeTag(cell, "right"), useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
        assertEquals(listOf(TangoMarker(cell, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)), board().markers)

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(centre(cell, n) + Offset(width / n * 0.45f, height / n * 0.45f))
            up()
        }
        assertEquals(mapOf(cell to TangoCell.MOON), board().givens)
        assertEquals(1, board().markers.size)
        assertEquals(3, edits.size)
    }

    private companion object {
        const val BOARD = "tango.editor.board"
        const val N = 6

        /** A 360 dp phone pane less 2 × Spacing.lg. */
        val PHONE_BOARD = 328.dp
        val ACROSS = TangoBoardSpec.editEdgeBandAcrossFraction
        val INSET = TangoBoardSpec.editEdgeBandInsetFraction

        /** Sweep points within this many cell units of a band outline are skipped (float edge). */
        const val SLACK = 1e-3f

        /** How far inside or outside a band a press probe lands, in cell units (≈ 1 dp at n10). */
        const val PROBE = 0.03f
    }
}
