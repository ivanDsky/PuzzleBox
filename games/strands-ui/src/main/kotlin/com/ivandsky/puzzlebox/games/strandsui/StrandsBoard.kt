package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsConnectors
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsGestureLayer
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsHaptics
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsTraceEndConnector
import com.ivandsky.puzzlebox.games.strandsui.board.TraceFinger
import com.ivandsky.puzzlebox.games.strandsui.board.altThemeCells
import com.ivandsky.puzzlebox.games.strandsui.board.altThemeLinks
import com.ivandsky.puzzlebox.games.strandsui.board.rememberTraceEndFrame
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.LocalSnapTrace
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

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
 */
@Composable
internal fun StrandsBoardContent(
    model: StrandsRendererModel,
    found: FoundEvent?,
    reduceMotion: Boolean,
    trace: () -> Trace,
    onTrace: (TraceStep) -> Unit,
    traceEnd: () -> TraceEnd? = { null },
    modifier: Modifier = Modifier,
) = StrandsBoardLayout(model, found, reduceMotion, trace, onTrace, remember { mutableStateOf<TraceFinger?>(null) }, traceEnd, modifier)

/**
 * [StrandsBoardContent] with the drag's [finger] handed in: the drag writes it and only the
 * connector's draw reads it, so a moving finger redraws the tip and recomposes nothing. A
 * preview holds a drag still by handing in the finger its session ended on.
 */
@Composable
internal fun StrandsBoardLayout(
    model: StrandsRendererModel,
    found: FoundEvent?,
    reduceMotion: Boolean,
    trace: () -> Trace,
    onTrace: (TraceStep) -> Unit,
    finger: MutableState<TraceFinger?>,
    traceEnd: () -> TraceEnd? = { null },
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val altCells = remember(model.links) { altThemeLinks(model.links).altThemeCells() }
    // Bumped by every touch-down: a running settle, sweep or hint pulse rests at once (A12).
    // Read only by the motion effects, so a touch-down recomposes nothing.
    val touchSeq = remember { mutableIntStateOf(0) }
    val end = rememberTraceEndFrame(traceEnd(), reduceMotion, touchSeq)
    // The Snap flag (SnapTrace), read once for the connector and every token.
    val snap = LocalSnapTrace.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = strandsCellSizeFor(maxWidth, maxHeight, model.rows, model.cols)
        val boardSize = DpSize(cell * model.cols, cell * model.rows)
        Box(Modifier.fillMaxWidth().height(boardSize.height), contentAlignment = Alignment.Center) {
            BoardGrid(
                rows = model.rows,
                cols = model.cols,
                modifier = Modifier.requiredSize(boardSize).testTag("strands.board"),
                // Strands has no grid: the letters float on the bare board.
                colors = colors.copy(boardGridLine = Color.Transparent),
                underlay = {
                    StrandsConnectors(model.rows, model.cols, model.links, trace, colors, { finger.value }, animate = !reduceMotion, snap = snap)
                    StrandsTraceEndConnector(model.rows, model.cols, { end.value }, colors)
                },
                cellContent = { row, col ->
                    LetterToken(
                        cell = model.cells[row * model.cols + col],
                        altTheme = Coord(row, col) in altCells,
                        pitch = cell,
                        found = found,
                        reduceMotion = reduceMotion,
                        touchSeq = touchSeq,
                        end = end,
                        snap = snap,
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
                onFinger = { finger.value = it },
            )
        }
    }
}
