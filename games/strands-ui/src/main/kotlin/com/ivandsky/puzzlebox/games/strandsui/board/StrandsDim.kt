package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink

/**
 * What the board draws faded, to [alpha]: the letters on [cells] and the connectors in
 * [links]. A cell on the live trace is never faded, whatever [cells] holds. Play draws none.
 */
internal class StrandsDim(
    val cells: Set<Coord>,
    val links: Set<StrandsLink>,
    val alpha: Float,
) {
    companion object {
        val None = StrandsDim(emptySet(), emptySet(), 1f)
    }
}

/** A fade in or out takes [MotionTokens.DurationShort], eased standard; reduced motion snaps. */
internal fun dimSpec(reduceMotion: Boolean): AnimationSpec<Float> =
    if (reduceMotion) snap() else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)
