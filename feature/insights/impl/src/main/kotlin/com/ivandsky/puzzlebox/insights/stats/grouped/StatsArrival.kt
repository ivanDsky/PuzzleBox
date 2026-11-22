package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.resultBeat
import kotlinx.coroutines.flow.first

/**
 * The page's arrival (M02): a block lifts in with the shared entrance, a fade plus a short rise. It plays the first
 * time [key] reaches the screen under a [StatsMotion] and never again, so scrolling back to a block does not replay
 * it. Until [visible] says the block is on screen it is hidden, so a block composed below the fold does not play its
 * entrance unseen. The blocks that arrive together (the page's first pass) start a stagger apart by [index]; a block
 * that arrives later, on a scroll, starts at once ([arrivalStagger]). Reduced motion is the entrance's own: one plain
 * fade, no travel and no scale. With no [StatsMotion] the block is untouched.
 *
 * It uses the first entrance beat, since the page has no hero to wait for.
 */
internal fun Modifier.statsArrival(index: Int, key: Any, visible: () -> Boolean = { true }): Modifier = composed {
    val motion = LocalStatsMotion.current
    val pending = remember(motion, key) { motion != null && !motion.hasArrived(key) }
    val staggered = remember(motion, key) { arrivalStagger(index, firstPass = motion?.firstPassDone == false) }
    var shown by remember(motion, key) { mutableStateOf(!pending) }
    var plays by remember(motion, key) { mutableStateOf(false) }
    if (pending && !shown) {
        LaunchedEffect(motion, key) {
            snapshotFlow(visible).first { it }
            plays = checkNotNull(motion).claimArrival(key)
            shown = true
        }
    }
    when {
        !shown -> this.alpha(0f)
        plays -> this.resultBeat(ResultBeats.Glyph, staggered)
        else -> this
    }
}
