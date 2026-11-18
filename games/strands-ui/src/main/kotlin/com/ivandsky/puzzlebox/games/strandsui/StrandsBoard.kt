package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.ivandsky.puzzlebox.designsystem.SolveMomentState
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.solveFrame
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.LocalPlayFeel
import com.ivandsky.puzzlebox.gameui.MoveHapticsEffect
import com.ivandsky.puzzlebox.gameui.replay.ReplayHintBadge
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
import com.ivandsky.puzzlebox.games.strandsui.board.strandsBoardColors
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor

/** The play screen's solve moment B, which the board wears; null without the solve rest and off the play screen. */
internal val LocalStrandsSolveMoment = staticCompositionLocalOf<SolveMomentState?> { null }

/** The game's glyph stamp for the board's box, inside the solve frame ([SolveStamp]); nothing without the solve rest. */
internal val LocalStrandsSolveStamp = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Strands' one board, chrome-free ([com.ivandsky.puzzlebox.gameui.GamePlayUi.Board]): the
 * daily screen and the tutorial both draw it. Draws nothing until [controller] holds a
 * puzzle. Every step goes through the controller first; with PlayFeel off the buzz is read from
 * what it made of the step ([StrandsHaptics]), and with PlayFeel on [MoveHapticsEffect] reads
 * [moveSnapshot] instead. A found word is taken from the controller once and played
 * from local state, so a board composed afresh (rotation, re-entry) rests instead of showing it again.
 * Under [LocalStrandsSolveMoment] the grid pops and is framed with the moment, and the word that solves the board
 * settles inside it ([FoundEvent.inSolveMoment]).
 */
@Composable
internal fun StrandsBoard(controller: StrandsPlayController, modifier: Modifier = Modifier) {
    val model = controller.baseModel
    if (model.rows == 0) return
    val haptics = LocalHapticFeedback.current
    val playFeel = LocalPlayFeel.current
    var playing by remember { mutableStateOf<FoundEvent?>(null) }
    val pending = controller.lastFound
    LaunchedEffect(pending) {
        if (pending != null) controller.takeFound()?.let { playing = it }
    }
    MoveHapticsEffect({ controller.moveSnapshot() }, ::strandsMoveHaptic)
    val moment = LocalStrandsSolveMoment.current
    val accent = StrandsBrand.accent.colors().accent
    // The word that solves the board settles inside the solve moment.
    val solving = moment != null && controller.isComplete
    // The pending event plays from its first frame; the taken copy carries it on (same seq, same trigger).
    val latest = pending ?: playing
    StrandsBoardContent(
        model = model,
        found = remember(latest, solving) { if (solving) latest?.copy(inSolveMoment = true) else latest },
        reduceMotion = rememberReducedMotion(),
        trace = { controller.trace },
        traceEnd = { controller.traceEnd },
        onTrace = { step ->
            val traceBefore = controller.trace
            val slotBefore = controller.slot
            controller.onTrace(step)
            if (!playFeel) {
                StrandsHaptics.forStep(step, traceBefore, controller.trace, slotBefore, controller.slot)
                    ?.let(haptics::performHapticFeedback)
            }
        },
        modifier = modifier,
        frame = remember(moment, accent) { moment?.let { Modifier.solveFrame(it, accent, RectangleShape) } ?: Modifier },
        stamp = LocalStrandsSolveStamp.current ?: {},
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
 * trace is never faded. [stamp] draws over the grid in [frame]'s box, so it pops with the board. [floor] is the least cell pitch ([strandsCellSizeFor]). [readOnly] drops the
 * gesture layer, so the board takes no drag (the replay board). [hintBadges] are the cells that wear the replay's hint badge in their top-right corner.
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
    readOnly: Boolean = false,
    hintBadges: Set<Coord> = emptySet(),
    frame: Modifier = Modifier,
    stamp: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = strandsBoardColors()
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
            // The frame's box holds the grid and the stamp, so the stamp pops with the board.
            Box(Modifier.requiredSize(boardSize).then(frame).testTag("strands.board"), contentAlignment = Alignment.Center) {
                BoardGrid(
                    rows = model.rows,
                    cols = model.cols,
                    modifier = Modifier.fillMaxSize(),
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
                        if (Coord(row, col) in hintBadges) ReplayHintBadge(Modifier.align(Alignment.TopEnd))
                    },
                )
                stamp()
            }
            if (!readOnly) {
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
}
