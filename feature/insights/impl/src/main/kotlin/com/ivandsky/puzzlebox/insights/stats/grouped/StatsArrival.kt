package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.resultBeat

/**
 * The page's arrival (M02): a block lifts in with the shared entrance, a fade plus a short rise, [index] blocks after
 * the first one starting a stagger later. It plays the first time [key] is composed under a [StatsMotion] and never
 * again, so scrolling back to a block does not replay it. Reduced motion is the entrance's own: one plain fade, no
 * travel and no scale. With no [StatsMotion] the block is untouched.
 *
 * It uses the first entrance beat, since the page has no hero to wait for.
 */
internal fun Modifier.statsArrival(index: Int, key: Any): Modifier = composed {
    val motion = LocalStatsMotion.current
    val arrives = remember(motion, key) { motion?.claimArrival(key) == true }
    if (arrives) this.resultBeat(ResultBeats.Glyph, index) else this
}
