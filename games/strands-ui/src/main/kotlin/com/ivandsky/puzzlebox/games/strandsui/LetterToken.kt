package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSemantics.letterButton
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSpec
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsCellDrawing
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsCellFrame
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsCellSpec
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsMotionCue
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsMotionTrigger
import com.ivandsky.puzzlebox.games.strandsui.board.TraceEndFrame
import com.ivandsky.puzzlebox.games.strandsui.board.TraceOverlaySpec
import com.ivandsky.puzzlebox.games.strandsui.board.at
import com.ivandsky.puzzlebox.games.strandsui.board.drawTokenSquare
import com.ivandsky.puzzlebox.games.strandsui.board.tokenColor

/** Called with a letter's cell each time its token composes; a no-op outside tests, which count recompositions with it. */
internal val LocalTokenComposed = staticCompositionLocalOf<(Coord) -> Unit> { {} }

/**
 * One letter's token: its cell state's look, the join fill and pop while it is on the trace,
 * and, once its path is submitted, [end]'s refusal or fade-out in place of both.
 */
@Composable
internal fun LetterToken(
    cell: StrandsCell,
    pitch: Dp,
    found: FoundEvent?,
    reduceMotion: Boolean,
    touchSeq: IntState,
    end: State<TraceEndFrame?>,
    colors: BoardColors,
    altTheme: Boolean,
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
    // A letter already traced when the token first composes (a restored board, a preview)
    // rests; only one joining afterwards fills and pops.
    val composed = remember { ComposedOnce() }
    SideEffect { composed.done = true }
    val joinTrigger = remember(traced) { JoinTrigger.takeIf { traced && composed.done } }
    val join = rememberCellFrame(TraceOverlaySpec, joinTrigger, reduceMotion, touchSeq)
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
                    val ended = end.value.at(cell.coord)
                    val scale = base.value.scale * join.value.scale * (ended?.scale ?: 1f)
                    scaleX = scale
                    scaleY = scale
                    translationX = ended?.offsetPx ?: 0f
                    alpha = ended?.alpha ?: 1f
                }
                .drawBehind {
                    val frame = base.value
                    val half = size.minDimension / 2
                    val drop = size.minDimension * StrandsBoardSpec.TokenLipFraction
                    val ended = end.value.at(cell.coord)
                    if (ended != null) {
                        drawTokenSquare(ended.lip(colors), half, drop = drop)
                        drawTokenSquare(ended.fill(colors), half)
                        return@drawBehind
                    }
                    val lip = if (altTheme) colors.strandsThemeAltLip else spec.lip(colors)
                    val fill = if (altTheme) colors.strandsThemeAltFill else spec.fill(colors)
                    tokenColor(lip, TraceOverlaySpec.lip(colors), traced, frame, join.value)?.let {
                        drawTokenSquare(it, half, drop = drop)
                    }
                    tokenColor(fill, TraceOverlaySpec.fill(colors), traced, frame, join.value)?.let { drawTokenSquare(it, half) }
                    with(spec) { decorate(colors, half, frame) }
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = cell.letter.toString(),
                style = MaterialTheme.typography.titleLarge.merge(fontSize = letterSize, fontWeight = FontWeight.Medium),
                color = {
                    val lit = join.value.lit
                    val ended = end.value.at(cell.coord)
                    when {
                        ended != null -> ended.letter(colors)
                        !traced -> spec.letter(colors)
                        lit >= 1f -> TraceOverlaySpec.letter(colors)
                        else -> lerp(spec.letter(colors), TraceOverlaySpec.letter(colors), lit)
                    }
                },
            )
        }
    }
}

/** A letter joining the trace fills and pops once; the key is constant because leaving the trace resets it. */
private val JoinTrigger = StrandsMotionTrigger(key = "join", cue = StrandsMotionCue())

/** Set after a token's first composition; deliberately not state, so setting it recomposes nothing. */
private class ComposedOnce {
    var done = false
}

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
