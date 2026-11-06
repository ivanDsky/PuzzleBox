package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSemantics.letterButton
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSpec
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsCellDrawing
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsCellFrame
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsCellSpec
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsConnectors
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsGestureLayer
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsHaptics
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsMotionCue
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsMotionTrigger
import com.ivandsky.puzzlebox.games.strandsui.board.TraceOverlaySpec
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor
import com.ivandsky.puzzlebox.gameui.BoardGrid
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
 * The stateless board: [model] (the board at rest) as round letter tokens over their
 * connectors, sized to the space it is given ([strandsCellSizeFor]) and centred in it, the
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    // Bumped by every touch-down: a running settle, sweep or hint pulse rests at once (A12).
    // Read only by the motion effects, so a touch-down recomposes nothing.
    val touchSeq = remember { mutableIntStateOf(0) }
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
                underlay = { StrandsConnectors(model.rows, model.cols, model.links, trace, colors) },
                cellContent = { row, col ->
                    LetterToken(
                        cell = model.cells[row * model.cols + col],
                        pitch = cell,
                        found = found,
                        reduceMotion = reduceMotion,
                        touchSeq = touchSeq,
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
            )
        }
    }
}

/** Called with a letter's cell each time its token composes; a no-op outside tests, which count recompositions with it. */
internal val LocalTokenComposed = staticCompositionLocalOf<(Coord) -> Unit> { {} }

@Composable
private fun LetterToken(
    cell: StrandsCell,
    pitch: Dp,
    found: FoundEvent?,
    reduceMotion: Boolean,
    touchSeq: IntState,
    colors: BoardColors,
    trace: () -> Trace,
    onTrace: (TraceStep) -> Unit,
) {
    LocalTokenComposed.current(cell.coord)
    val traceIndex by remember(cell.coord, trace) {
        derivedStateOf { trace().path.indexOf(cell.coord).takeIf { it >= 0 } }
    }
    val traced = traceIndex != null
    val onTap = remember(cell.coord, trace, onTrace) { { onTrace(TraceInput.tap(trace(), cell.coord)) } }
    val spec = StrandsCellDrawing.specOf(cell.state)
    val base = rememberCellFrame(spec, spec.trigger(cell, found), reduceMotion, touchSeq)
    val press = rememberCellFrame(TraceOverlaySpec, PressTrigger.takeIf { traced }, reduceMotion, touchSeq)
    val tokenSize = pitch * StrandsBoardSpec.TokenFraction
    // Pitch-proportional and immune to font scale, like the token it sits in.
    val letterSize = with(LocalDensity.current) { (pitch * StrandsBoardSpec.LetterFraction).toSp() }
    Box(
        Modifier
            .fillMaxSize()
            .testTag("strands.cell.${cell.coord.row}.${cell.coord.col}")
            .letterButton(cell.copy(traceIndex = traceIndex), onTap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(tokenSize)
                .graphicsLayer {
                    val scale = base.value.scale * press.value.scale
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    val frame = base.value
                    val radius = size.minDimension / 2
                    tokenFill(spec, traced, frame, colors)?.let { drawCircle(it, radius) }
                    with(spec) { decorate(colors, radius, frame) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.letter.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontSize = letterSize,
                fontWeight = FontWeight.Medium,
                color = if (traced) TraceOverlaySpec.letter(colors) else spec.letter(colors),
            )
        }
    }
}

/** The live trace overlays any base look; a found letter turns from the trace colour to its own as it lights. */
private fun tokenFill(spec: StrandsCellSpec, traced: Boolean, frame: StrandsCellFrame, colors: BoardColors): Color? {
    if (traced) return TraceOverlaySpec.fill(colors)
    val fill = spec.fill(colors) ?: return null
    return if (frame.lit >= 1f) fill else lerp(TraceOverlaySpec.fill(colors), fill, frame.lit)
}

/** A letter joining the trace pulses once; the key is constant because leaving the trace resets it. */
private val PressTrigger = StrandsMotionTrigger(key = "press", cue = StrandsMotionCue())

/**
 * Plays [spec]'s timeline from each new [trigger] on the frame clock and holds
 * [StrandsCellFrame.Rest] otherwise. Read in the draw phase, so a frame redraws one token
 * without recomposing the board. A touch after the trigger rests it at once.
 */
@Composable
private fun rememberCellFrame(
    spec: StrandsCellSpec,
    trigger: StrandsMotionTrigger?,
    reduceMotion: Boolean,
    touchSeq: IntState,
): State<StrandsCellFrame> {
    val frame = remember(spec, trigger) {
        mutableStateOf(if (trigger == null) StrandsCellFrame.Rest else spec.frame(0, trigger.cue, reduceMotion))
    }
    // Unobserved: a touch-down must not recompose the token, only stop its motion below.
    val bornTouch = remember(spec, trigger) { Snapshot.withoutReadObservation { touchSeq.intValue } }
    LaunchedEffect(spec, trigger, reduceMotion) {
        if (trigger == null || reduceMotion || touchSeq.intValue != bornTouch) {
            frame.value = StrandsCellFrame.Rest
            return@LaunchedEffect
        }
        val total = spec.motionMs(trigger.cue)
        val start = withFrameMillis { it }
        var elapsed = 0L
        while (elapsed < total) {
            elapsed = withFrameMillis { it } - start
            if (touchSeq.intValue != bornTouch) break
            frame.value = spec.frame(elapsed.toInt(), trigger.cue, reduceMotion = false)
        }
        frame.value = StrandsCellFrame.Rest
    }
    return frame
}
