package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.animation.core.animateFloatAsState
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
import com.ivandsky.puzzlebox.games.strandsui.board.strandsStroke
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPockets
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoardContent
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.board.dimSpec
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * The editor's one 8×6 board, drawn by the play renderer over the [controller]'s draft: each
 * traced path as a found word (the spangram's in its own look), blank cells as empty tiles
 * under the line ([StrandsIdleTiles]), and the live trace over them, its cells as trace
 * tokens. Touch is the play board's: a drag or taps trace cells, a lift commits the
 * path ([StrandsEditorController.endTrace]); a tap on a traced cell or its chip selects that
 * path, which keeps its word look while everything else fades ([dimOf]), whose ends
 * a drag from one shortens or extends it ([StrandsEndDrag]); uncovered areas too small
 * for a word get a dashed outline (`strands.editor.cell.<idx>.pocket`). The [verdict] and the card's witness choice
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
 * ([StrandsWordField]), which names the selected path and is off while none is selected, and
 * [StrandsPathsPanel], one row per path.
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
    val model = remember(board, live) { modelOf(board, live) }
    val dim = remember(board, selected) { dimOf(board, selected) }
    val reduceMotion = rememberReducedMotion()
    // The tiles are the editor's own, so they fade here, with the board's letters and connectors.
    val tileAlpha = animateFloatAsState(dim.alpha, dimSpec(reduceMotion), label = "tileDim")
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
            reduceMotion = reduceMotion,
            trace = trace,
            onTrace = controller::step,
            tiles = { StrandsIdleTiles(idle, Modifier.fillMaxSize().graphicsLayer { alpha = tileAlpha.value }) },
            onFinger = controller::fingerAt,
            dim = dim,
            modifier = Modifier.fillMaxSize().testTag("strands.editor.board"),
        )
        StrandsDiagnosticsLayer(diagnostics, Modifier.fillMaxSize())
        CellTaps(
            onTap = controller::tapCell,
            pockets = pockets,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * One pointer-transparent node per cell, laid over the board exactly as the board lays itself
 * out, so the cell's tag (`strands.editor.cell.<idx>`) answers a click as the board's own tap.
 * It also carries the pocket outlines above the letters.
 */
@Composable
private fun CellTaps(
    onTap: (Int) -> Unit,
    pockets: Set<Int>,
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
    val width = strandsStroke(2.dp, size.minDimension)
    val dash = strandsStroke(4.dp, size.minDimension)
    drawRect(
        color = color,
        topLeft = Offset(width, width),
        size = Size(size.width - 2 * width, size.height - 2 * width),
        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}
