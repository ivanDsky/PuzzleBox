package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's one tab strip (Community, Games): equal-width [tabs] that are [TabHeight] tall, a 1dp
 * `outlineVariant` divider under them, and a [TabIndicatorHeight] bar under the [selected] tab. The
 * selected label and the bar are `primaryText`, the rest `onSurfaceVariant`. A tap calls
 * [onSelect] with the tab's index. When [selected] changes the bar slides over
 * [MotionTokens.DurationMedium] on [MotionTokens.EasingStandard]; under reduced motion it jumps.
 * For a position a pager drives, see [UnderlineTabsAt].
 */
@Composable
fun UnderlineTabs(
    tabs: List<UnderlineTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val position = animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = if (reduced) snap() else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
        label = "underline-tabs-position",
    )
    UnderlineTabStrip(tabs, { position.value }, onSelect, modifier)
}
