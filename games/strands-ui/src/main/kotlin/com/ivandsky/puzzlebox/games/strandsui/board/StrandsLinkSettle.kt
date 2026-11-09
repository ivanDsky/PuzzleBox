package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
 */
internal class LinkSettle(val link: StrandsLink, val spec: StrandsCellSpec, val elapsedMs: Int) {

    private fun frames(): List<StrandsCellFrame> = List(link.cells.size) {
        spec.frame(elapsedMs, StrandsMotionCue(it, link.cells.size), reduceMotion = false)
    }

    /** Each step's thickness relative to rest: the smaller [StrandsCellFrame.scale] of its two letters. */
    fun stepScales(): FloatArray = frames().let { f -> FloatArray(f.size - 1) { minOf(f[it].scale, f[it + 1].scale) } }

    /** How far each step has turned from the trace's look to the link's own: the lesser [StrandsCellFrame.lit] of its two letters. */
    fun stepLit(): FloatArray = frames().let { f -> FloatArray(f.size - 1) { minOf(f[it].lit, f[it + 1].lit) } }
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
            settle.value = LinkSettle(first.link, first.spec, elapsed.toInt())
        }
        settle.value = null
    }
    return settle
}
