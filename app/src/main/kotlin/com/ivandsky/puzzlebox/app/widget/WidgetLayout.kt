package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What the widget draws with at the size the launcher gave it ([of]): the [step], its fixed [look], and the fixed-size block
 * ([contentWidth] by [contentHeight]) the widget centres. Sizes never grow with the widget: past the block's largest size the
 * extra space is background around it. Until the list steps have layouts of their own they draw the chips2 look.
 */
internal data class WidgetLayout(
    val step: WidgetStep,
    val look: ChipLook,
    val contentWidth: Dp,
    val contentHeight: Dp,
) {
    /** The fixed sizes of a chip row's step: the streak line, the chips and whether the second line and the pill exist. */
    data class ChipLook(
        val horizontalPadding: Dp,
        val verticalPadding: Dp,
        val ring: Dp,
        val streakText: TextUnit,
        val flame: Dp,
        val longStreak: Boolean,
        val timeLine: Boolean,
        val pill: Boolean,
    )

    companion object {
        val MAX_CONTENT_WIDTH: Dp = 440.dp

        /** The chips2 block's height at the top of its step: 280dp less its padding. Taller widgets centre it. */
        val MAX_CONTENT_HEIGHT: Dp = 252.dp

        /** The 48dp touch-target floor: the pill's height. */
        val PILL_HEIGHT: Dp = 48.dp

        val SNOWFLAKE: Dp = 14.dp

        /** The content width from which the streak reads "N days in a row" on chips2. */
        val LONG_STREAK_FROM: Dp = 320.dp

        private val CHIPS1 = ChipLook(
            horizontalPadding = 10.dp,
            verticalPadding = 8.dp,
            ring = 32.dp,
            streakText = 16.sp,
            flame = 18.dp,
            longStreak = false,
            timeLine = false,
            pill = false,
        )

        private val CHIPS2 = ChipLook(
            horizontalPadding = 14.dp,
            verticalPadding = 14.dp,
            ring = 36.dp,
            streakText = 17.sp,
            flame = 19.dp,
            longStreak = true,
            timeLine = true,
            pill = true,
        )

        fun of(size: DpSize): WidgetLayout {
            val step = WidgetStep.of(size)
            val base = if (step == WidgetStep.Chips1) CHIPS1 else CHIPS2
            val contentWidth = (size.width - base.horizontalPadding * 2).coerceAtMost(MAX_CONTENT_WIDTH)
            // "658 days in a row" plus the freezes and "5 of 5 done" do not fit the 4-cell content width; the short words do.
            val look = if (base.longStreak && contentWidth < LONG_STREAK_FROM) base.copy(longStreak = false) else base
            return WidgetLayout(
                step = step,
                look = look,
                contentWidth = contentWidth,
                contentHeight = (size.height - look.verticalPadding * 2).coerceAtMost(MAX_CONTENT_HEIGHT),
            )
        }
    }
}
