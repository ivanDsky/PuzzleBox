package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsDragSession
import com.ivandsky.puzzlebox.games.strandsui.board.TraceFinger
import com.ivandsky.puzzlebox.games.strandsui.board.centreOf
import kotlin.math.floor

/**
 * One pane per board state, light and dark (@PuzzleBoxPreviews), built from real
 * [StrandsState] values through the same [StrandsRendererModel] the play screen draws, at
 * rest (reduced motion). The chrome's panes live in chrome/StrandsChromePreviews.kt.
 * The board is a 4×4: PIES, BAKE (the spangram, edge to edge), TART, CAKE.
 */
private val previewPuzzle: StrandsPuzzle by lazy {
    val rows = listOf("PIES", "BAKE", "TART", "CAKE")
    val words = rows.mapIndexed { row, text -> StrandsWord(text, text.indices.map { Coord(row, it) }) }
    StrandsPuzzle(
        rows = 4,
        cols = 4,
        letters = rows.joinToString(""),
        clue = "Bake sale",
        spangram = words[1],
        themeWords = words - words[1],
        validWords = rows.toSet(),
        constructors = "PuzzleBox",
    )
}

/** [trace] reaches the board as its live trace, as the play screen's does, not through the model; [finger] is a held drag. */
@Composable
private fun BoardPane(
    state: StrandsState = StrandsState(previewPuzzle),
    trace: List<Coord> = emptyList(),
    finger: TraceFinger? = null,
) {
    PuzzleBoxTheme {
        StrandsBoardLayout(
            model = StrandsRendererModel.of(state),
            found = null,
            reduceMotion = true,
            trace = { Trace(trace) },
            onTrace = {},
            finger = remember { mutableStateOf(finger) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A drag held where [points] (cell units) leave it, walked by the real session from the
 * letter under the first, so a tip preview shows only a trace and finger a drag can make.
 */
private fun heldDrag(points: List<Offset>): Pair<List<Coord>, TraceFinger?> {
    val down = points.first()
    val start = Coord(floor(down.y).toInt(), floor(down.x).toInt())
    val session = StrandsDragSession(previewPuzzle.rows, previewPuzzle.cols, start, down)
    var trace = Trace()
    for (point in points.drop(1)) session.move(point, trace).forEach { trace = (it as TraceStep.Updated).trace }
    return trace.path to session.finger(trace)
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardIdlePreview() = BoardPane()

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardTracingPreview() =
    BoardPane(trace = listOf(Coord(2, 0), Coord(2, 1), Coord(3, 2)))

/** A4: mid-drag toward the free T at (2,3): the line end follows the finger past the tail. */
@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardTipForwardPreview() {
    val (trace, finger) = heldDrag(listOf(centreOf(Coord(2, 0)), centreOf(Coord(2, 2)) + Offset(0.45f, 0.02f)))
    BoardPane(trace = trace, finger = finger)
}

/** A4: dragging into the used B at (1,0): the tip only nudges, it never stretches into a used letter. */
@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardTipNudgedPreview() {
    val (trace, finger) = heldDrag(
        listOf(
            centreOf(Coord(1, 0)),
            centreOf(Coord(1, 1)),
            centreOf(Coord(2, 1)),
            centreOf(Coord(2, 0)),
            centreOf(Coord(2, 0)) + Offset(0f, -0.6f),
        ),
    )
    BoardPane(trace = trace, finger = finger)
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardFoundPreview() =
    BoardPane(StrandsState(previewPuzzle, found = listOf("PIES", "BAKE")))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardHintOutlinedPreview() =
    BoardPane(StrandsState(previewPuzzle, hintsUsed = 1, activeHint = ActiveHint("TART", HintStage.Outlined)))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardHintOrderPreview() =
    BoardPane(StrandsState(previewPuzzle, hintsUsed = 2, activeHint = ActiveHint("TART", HintStage.OrderRevealed)))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardSolvedPreview() =
    BoardPane(StrandsState(previewPuzzle, found = listOf("PIES", "BAKE", "TART", "CAKE")))

/** A tablet in landscape gives the board a short, wide pane: the cell binds on height (A37). */
@PuzzleBoxPreviews
@Preview(name = "Expanded landscape", widthDp = 640, heightDp = 220, showBackground = true)
@Composable
internal fun StrandsBoardShortPanePreview() =
    BoardPane(StrandsState(previewPuzzle, found = listOf("BAKE")), trace = listOf(Coord(0, 0), Coord(0, 1)))
