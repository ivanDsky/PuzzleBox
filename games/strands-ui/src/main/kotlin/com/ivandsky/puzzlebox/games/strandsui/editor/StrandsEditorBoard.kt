package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPockets
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoardContent
import com.ivandsky.puzzlebox.games.strandsui.Trace
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import com.ivandsky.puzzlebox.game.editor.Verdict

/** What a blank cell shows in place of a letter. */
private const val BLANK_GLYPH = ' '

/**
 * The editor's one 8×6 board, drawn by the play renderer over the [controller]'s draft: each
 * traced path as a found word (the spangram's in its own look), blank cells as empty tiles
 * under the line ([StrandsIdleTiles]), and the live trace over them, its cells as trace
 * tokens. Touch is the play board's: a drag or taps trace cells, a lift commits the
 * path ([StrandsEditorController.endTrace]); a tap on a traced cell or its chip selects that
 * path, drawn outlined, a ring on each of its ends: a drag from one shortens or extends it
 * ([StrandsEndDrag]); uncovered areas too small for a word get a dashed outline
 * (`strands.editor.cell.<idx>.pocket`). The [verdict] and the card's witness choice
 * ([StrandsEditorController.overlay]) are drawn by [StrandsDiagnosticsLayer]. With [withTools]
 * the [StrandsAuthoringTools] follow the grid in one scroll and the grid keeps its width's touch
 * size; without them (the tablet Studio draws them in its side pane) the grid alone fits the
 * width and the height, centred, with no scroll.
 */
@Composable
internal fun StrandsEditorBoard(
    controller: StrandsEditorController,
    modifier: Modifier = Modifier,
    verdict: Verdict = Verdict.Checking,
    withTools: Boolean = true,
) {
    BoxWithConstraints(modifier) {
        if (withTools) {
            // The grid keeps its touch size and the column scrolls, so the tools never squeeze the board.
            val cell = strandsCellSizeFor(maxWidth, Dp.Infinity, StrandsDraft.ROWS, StrandsDraft.COLS)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StrandsEditorGrid(controller, verdict, Modifier.fillMaxWidth().height(cell * StrandsDraft.ROWS))
                StrandsAuthoringTools(controller)
            }
        } else {
            val cell = strandsCellSizeFor(maxWidth, maxHeight, StrandsDraft.ROWS, StrandsDraft.COLS)
            StrandsEditorGrid(controller, verdict, Modifier.align(Alignment.Center).fillMaxWidth().height(cell * StrandsDraft.ROWS))
        }
    }
}

/**
 * The authoring controls under the grid: the selected path's spangram toggle, the word field
 * ([StrandsWordField]), which names the selected path or, with none, the word the next lift
 * writes, and [StrandsPathsPanel], one row per path.
 */
@Composable
internal fun StrandsAuthoringTools(controller: StrandsEditorController, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val selected by controller.selectedPath.collectAsState()
    val board = draft.state.draft as StrandsDraft
    Column(modifier) {
        selected?.let { index ->
            StrandsSpangramToggle(
                index = index,
                isSpangram = board.spangram == index,
                onToggle = { controller.toggleSpangram(index) },
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        StrandsWordField(controller)
        StrandsPathsPanel(
            board = board,
            selected = selected,
            onSelect = controller::selectPath,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** The 8×6 grid with its diagnostics and cell taps, filling [modifier]. */
@Composable
private fun StrandsEditorGrid(controller: StrandsEditorController, verdict: Verdict, modifier: Modifier) {
    val draft by controller.draft.collectAsState()
    val live by controller.trace.collectAsState()
    val selected by controller.selectedPath.collectAsState()
    val board = draft.state.draft as StrandsDraft
    val model = remember(board, live, selected) { modelOf(board, live, selected) }
    val overlay by controller.overlay.collectAsState()
    // The last settled verdict, so the witness paths hold while a new analysis runs.
    val settled = remember { mutableStateOf<Verdict?>(null) }
    SideEffect { if (verdict != Verdict.Checking) settled.value = verdict }
    val held = if (verdict == Verdict.Checking) settled.value else verdict
    val diagnostics = strandsDiagnostics(verdict, held, overlay)
    val pockets = remember(board.paths) { StrandsPockets.of(board).flatten().toSet() }
    val covered = remember(board.paths, live) { board.paths.flatMap { it.cells }.toSet() + live.toSet() }
    val idle = (0 until StrandsDraft.ROWS * StrandsDraft.COLS).toSet() - covered
    // A token derives its trace place from this lambda, and the flow's value is no snapshot
    // read: keyed on the collected trace, each step hands the tokens a new one to derive from.
    val trace = remember(live) { { Trace(controller.trace.value.map(::coordOf)) } }
    Box(modifier) {
        StrandsBoardContent(
            model = model,
            found = null,
            reduceMotion = rememberReducedMotion(),
            trace = trace,
            onTrace = controller::step,
            tiles = { StrandsIdleTiles(idle, Modifier.fillMaxSize()) },
            modifier = Modifier.fillMaxSize().testTag("strands.editor.board"),
        )
        StrandsDiagnosticsLayer(diagnostics, Modifier.fillMaxSize())
        CellTaps(
            onTap = controller::tapCell,
            pockets = pockets,
            ends = board.endsOf(selected),
            endColor = selected?.let { LocalBoardColors.current.tintOf(board, it) } ?: Color.Unspecified,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * One pointer-transparent node per cell, laid over the board exactly as the board lays itself
 * out, so the cell's tag (`strands.editor.cell.<idx>`) answers a click as the board's own tap.
 * It also carries the pocket outlines and the selected path's end rings, above the letters.
 */
@Composable
private fun CellTaps(
    onTap: (Int) -> Unit,
    pockets: Set<Int>,
    ends: Set<Int>,
    endColor: Color,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.error
    BoxWithConstraints(modifier) {
        val cell = strandsCellSizeFor(maxWidth, maxHeight, StrandsDraft.ROWS, StrandsDraft.COLS)
        Box(Modifier.fillMaxWidth().height(cell * StrandsDraft.ROWS), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(cell * StrandsDraft.COLS, cell * StrandsDraft.ROWS)) {
                Column {
                    repeat(StrandsDraft.ROWS) { row ->
                        Row {
                            repeat(StrandsDraft.COLS) { col ->
                                val idx = row * StrandsDraft.COLS + col
                                Box(
                                    Modifier
                                        .size(cell)
                                        .testTag("strands.editor.cell.$idx")
                                        .semantics {
                                            invisibleToUser()
                                            onClick { onTap(idx); true }
                                        },
                                ) {
                                    if (idx in pockets) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .drawBehind { drawPocketOutline(outline) }
                                                .testTag("strands.editor.cell.$idx.pocket"),
                                        )
                                    }
                                    if (idx in ends) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .drawBehind { drawEndRing(endColor) }
                                                .testTag("strands.editor.cell.$idx.end"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A dashed rectangle just inside the cell: the mark of a cell no word can fill. */
private fun DrawScope.drawPocketOutline(color: Color) {
    val width = 2.dp.toPx()
    val dash = 4.dp.toPx()
    drawRect(
        color = color,
        topLeft = Offset(width, width),
        size = Size(size.width - 2 * width, size.height - 2 * width),
        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

private fun coordOf(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)

private fun modelOf(board: StrandsDraft, live: List<Int>, selected: Int?): StrandsRendererModel {
    val states = HashMap<Int, CellState>()
    board.paths.forEachIndexed { index, path ->
        val state = when {
            index == selected -> CellState.HintOutlined
            index == board.spangram -> CellState.FoundSpangram
            else -> CellState.FoundTheme
        }
        path.cells.forEach { states[it] = state }
    }
    val trace = live.map(::coordOf)
    val cells = List(StrandsDraft.ROWS * StrandsDraft.COLS) { cell ->
        val letter = board.letters[cell].takeIf { it != StrandsDraft.BLANK } ?: BLANK_GLYPH
        StrandsCell(coordOf(cell), letter, states[cell] ?: CellState.Idle, live.indexOf(cell).takeIf { it >= 0 })
    }
    val links = board.paths.mapIndexed { index, path ->
        StrandsLink(path.cells.map(::coordOf), if (index == board.spangram) LinkKind.Spangram else LinkKind.Theme)
    } + listOfNotNull(StrandsLink(trace, LinkKind.Trace).takeIf { trace.isNotEmpty() })
    return StrandsRendererModel(
        rows = StrandsDraft.ROWS,
        cols = StrandsDraft.COLS,
        cells = cells,
        links = links,
        foundCount = board.paths.size,
        totalCount = board.paths.size,
        complete = false,
    )
}
