package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsConnectors
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSpec
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsDim
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsGestureLayer
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsHaptics
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsTraceEndConnector
import com.ivandsky.puzzlebox.games.strandsui.board.altThemeCells
import com.ivandsky.puzzlebox.games.strandsui.board.altThemeLinks
import com.ivandsky.puzzlebox.games.strandsui.board.rememberLinkSettle
import com.ivandsky.puzzlebox.games.strandsui.board.rememberTraceEndFrame
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor

/**
 * Strands' one board, chrome-free ([com.ivandsky.puzzlebox.gameui.GamePlayUi.Board]): the
 * daily screen and the tutorial both draw it. Draws nothing until [controller] holds a
 * puzzle. Every step goes through the controller first; the buzz is read from what it made
 * of the step ([StrandsHaptics]). A found word is taken from the controller once and played
 * from local state, so a board composed afresh (rotation, re-entry) rests instead of replaying it.
 */
@Composable
internal fun StrandsBoard(controller: StrandsPlayController, modifier: Modifier = Modifier) {
    val model = controller.baseModel
    if (model.rows == 0) return
    val haptics = LocalHapticFeedback.current
    var playing by remember { mutableStateOf<FoundEvent?>(null) }
    val pending = controller.lastFound
    LaunchedEffect(pending) {
        if (pending != null) controller.takeFound()?.let { playing = it }
    }
    StrandsBoardContent(
        model = model,
        // The pending event plays from its first frame; the taken copy carries it on (same seq, same trigger).
        found = pending ?: playing,
        reduceMotion = rememberReducedMotion(),
        trace = { controller.trace },
        traceEnd = { controller.traceEnd },
        onTrace = { step ->
            val traceBefore = controller.trace
            val slotBefore = controller.slot
            controller.onTrace(step)
            StrandsHaptics.forStep(step, traceBefore, controller.trace, slotBefore, controller.slot)
                ?.let(haptics::performHapticFeedback)
        },
        modifier = modifier,
    )
}

/**
 * The stateless board: [model] (the board at rest) as letter tokens, rounded squares on
 * their lips, over their connectors, sized to the space it is given ([strandsCellSizeFor]) and centred in it, the
 * whole width being its touch area. [found] is the word just found, whose letters settle or
 * sweep. [trace] is never read here: each letter derives its own place in it and the trace
 * connector reads it while drawing, so a trace step recomposes only the letters it changes.
 * [tiles] draws first in the board's underlay, under every connector and letter (the
 * editor's empty tiles), so the live line is never covered. [onFinger] gets the dragging
 * finger in cell units after each move's steps, and null at the lift, before the lift's step.
 * [dim] fades the letters and connectors it names (the editor's unselected paths); the live
 * trace is never faded. [floor] is the least cell pitch ([strandsCellSizeFor]).
 */
@Composable
internal fun StrandsBoardContent(
    model: StrandsRendererModel,
    found: FoundEvent?,
    reduceMotion: Boolean,
    trace: () -> Trace,
    onTrace: (TraceStep) -> Unit,
    traceEnd: () -> TraceEnd? = { null },
    tiles: @Composable BoxScope.() -> Unit = {},
    onFinger: (Offset?) -> Unit = {},
    dim: StrandsDim = StrandsDim.None,
    floor: Dp = StrandsBoardSpec.TouchFloor,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val altCells = remember(model.links) { altThemeLinks(model.links).altThemeCells() }
    // Bumped by every touch-down: a running settle, sweep or hint pulse rests at once (A12).
    // Read only by the motion effects, so a touch-down recomposes nothing.
    val touchSeq = remember { mutableIntStateOf(0) }
    // The drag writes the finger (cell units); only the connector's draw reads it, so a moving finger redraws the tip and recomposes nothing.
    val finger = remember { mutableStateOf<Offset?>(null) }
    // The tip leans only at letters the board would join next — never one already found (A4).
    val foundLetter = remember(model.cells) {
        val foundCells = model.cells
            .filter { it.state == CellState.FoundTheme || it.state == CellState.FoundSpangram }
            .mapTo(HashSet()) { it.coord }
        val isFound: (Coord) -> Boolean = { it in foundCells }
        isFound
    }
    val end = rememberTraceEndFrame(traceEnd(), reduceMotion, touchSeq)
    val settle = rememberLinkSettle(found, model.links, reduceMotion, touchSeq)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = strandsCellSizeFor(maxWidth, maxHeight, model.rows, model.cols, floor)
        val boardSize = DpSize(cell * model.cols, cell * model.rows)
        Box(Modifier.fillMaxWidth().height(boardSize.height), contentAlignment = Alignment.Center) {
            BoardGrid(
                rows = model.rows,
                cols = model.cols,
                modifier = Modifier.requiredSize(boardSize).testTag("strands.board"),
                // Strands has no grid: the letters float on the bare board.
                colors = colors.copy(boardGridLine = Color.Transparent),
                underlay = {
                    tiles()
                    StrandsConnectors(model.rows, model.cols, model.links, trace, colors, { finger.value }, foundLetter, animate = !reduceMotion, settle = { settle.value }, dim = dim)
                    StrandsTraceEndConnector(model.rows, model.cols, { end.value }, colors)
                },
                cellContent = { row, col ->
                    LetterToken(
                        cell = model.cells[row * model.cols + col],
                        altTheme = Coord(row, col) in altCells,
                        dim = dim,
                        pitch = cell,
                        found = found,
                        reduceMotion = reduceMotion,
                        touchSeq = touchSeq,
                        end = end,
                        colors = colors,
                        trace = trace,
                        onTrace = onTrace,
                    )
                },
            )
            StrandsGestureLayer(
                rows = model.rows,
                cols = model.cols,
                boardSize = boardSize,
                trace = trace,
                onTrace = onTrace,
                onTouch = { touchSeq.intValue++ },
                onFinger = {
                    finger.value = it
                    onFinger(it)
                },
            )
        }
    }
}
