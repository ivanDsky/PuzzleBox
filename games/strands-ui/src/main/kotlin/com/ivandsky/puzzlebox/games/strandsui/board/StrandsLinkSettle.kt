package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strandsui.FoundEvent

/**
 * A found word's link [elapsedMs] into its settle. It is drawn from its letters' own frames
 * ([spec] is theirs): it carries on the trace line until its letters start, then dips and
 * swells with them, never thicker than the tiles it joins.
 *
 * One instance plays the whole settle: [elapsedMs] is state, read in the draw phase, and the two
 * step arrays are sized once from the link and refilled in place when it moves.
 */
internal class LinkSettle(val link: StrandsLink, val spec: StrandsCellSpec, elapsedMs: Int) {

    private val elapsed = mutableIntStateOf(elapsedMs)

    var elapsedMs: Int
        get() = elapsed.intValue
        set(value) {
            elapsed.intValue = value
        }

    private val scales = FloatArray((link.cells.size - 1).coerceAtLeast(0))
    private val lit = FloatArray((link.cells.size - 1).coerceAtLeast(0))
    private var filledFor = -1

    /**
     * Each step's thickness relative to rest: the smaller [StrandsCellFrame.scale] of its two
     * letters. The returned array is reused: valid until [elapsedMs] changes.
     */
    fun stepScales(): FloatArray = scales.also { fill() }

    /**
     * How far each step has turned from the trace's look to the link's own: the lesser
     * [StrandsCellFrame.lit] of its two letters. The returned array is reused: valid until
     * [elapsedMs] changes.
     */
    fun stepLit(): FloatArray = lit.also { fill() }

    private fun fill() {
        val now = elapsedMs
        if (now == filledFor) return
        filledFor = now
        val count = link.cells.size
        var from = spec.frame(now, StrandsMotionCue(0, count), reduceMotion = false)
        for (i in scales.indices) {
            val to = spec.frame(now, StrandsMotionCue(i + 1, count), reduceMotion = false)
            scales[i] = minOf(from.scale, to.scale)
            lit[i] = minOf(from.lit, to.lit)
            from = to
        }
    }
}

/**
 * [found]'s link settling on the frame clock, beside its letters' own: same start frame,
 * same timeline. It is null before the word is found, after the settle ends, under reduced
 * motion and from the first touch-down. Read in the draw phase, so a frame redraws the
 * connectors without recomposing the board.
 */
@Composable
internal fun rememberLinkSettle(
    found: FoundEvent?,
    links: List<StrandsLink>,
    reduceMotion: Boolean,
    touchSeq: IntState,
): State<LinkSettle?> {
    val settle = remember(found?.seq, reduceMotion) {
        val kind = if (found?.spangram == true) LinkKind.Spangram else LinkKind.Theme
        val link = found?.let { event -> links.firstOrNull { it.kind == kind && it.cells == event.cells } }
        val spec = if (kind == LinkKind.Spangram) FoundSpangramSpec else FoundThemeSpec
        val start = link?.takeUnless { reduceMotion || it.cells.size < 2 }?.let { LinkSettle(it, spec, 0) }
        mutableStateOf(start)
    }
    // Unobserved: a touch-down must not recompose the board, only stop the motion below.
    val bornTouch = remember(settle) { Snapshot.withoutReadObservation { touchSeq.intValue } }
    LaunchedEffect(settle) {
        val first = settle.value ?: return@LaunchedEffect
        val total = first.spec.motionMs(StrandsMotionCue(first.link.cells.lastIndex, first.link.cells.size))
        val start = withFrameMillis { it }
        var elapsed = 0L
        while (elapsed < total) {
            elapsed = withFrameMillis { it } - start
            if (touchSeq.intValue != bornTouch) break
            first.elapsedMs = elapsed.toInt()
        }
        settle.value = null
    }
    return settle
}
