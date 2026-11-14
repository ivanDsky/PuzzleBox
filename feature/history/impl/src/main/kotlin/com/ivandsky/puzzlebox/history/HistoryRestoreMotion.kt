package com.ivandsky.puzzlebox.history

import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * The list's insert motion, the one Create's rows use (MotionTokens medium, EasingStandard): an item **Fades in** and
 * slides to its place, and one that leaves **Fades out**; none under [reduced] motion. It is on only for a [restored]
 * history, one that showed the first cloud restore's card (A34), so the card fades out as the restored rows fill in;
 * any other list changes as it always did.
 */
internal fun LazyItemScope.restoreMotion(restored: Boolean, reduced: Boolean): Modifier = if (!restored) {
    Modifier
} else {
    Modifier.animateItem(
        fadeInSpec = if (reduced) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
        placementSpec = if (reduced) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
        fadeOutSpec = if (reduced) null else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
    )
}
