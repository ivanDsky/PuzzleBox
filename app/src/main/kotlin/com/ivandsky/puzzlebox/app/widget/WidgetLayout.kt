package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * What the widget draws with at the size the launcher gave it ([of]): the [step], its fixed [look], and the fixed-size block
 * ([contentWidth] by [contentHeight]) the widget centres. Sizes never grow with the widget: past the block's largest size the
 * extra space is background around it. The list steps also have a [rowHeight], the one size that follows the widget's height,
 * and [newBestChip], whether the narrow list has room for the New best mark.
 */
internal data class WidgetLayout(
    val step: WidgetStep,
    val look: Look,
    val contentWidth: Dp,
    val contentHeight: Dp,
    val rowHeight: Dp,
    val newBestChip: Boolean,
) {
    /**
     * The fixed sizes of a step: the streak line or header, the ring, and whether the chips have a second line and the step
     * has an action pill.
     */
    data class Look(
        val horizontalPadding: Dp,
        val verticalPadding: Dp,
        val ring: Dp,
        val streakText: TextUnit,
        val flame: Dp,
        val longStreak: Boolean,
        val timeLine: Boolean,
        val pill: Boolean,
    ) {
        /** The streak line's height: the flame and a little air. */
        val streakHeight: Dp get() = flame + 4.dp
    }

    companion object {
        val MAX_CONTENT_WIDTH: Dp = 440.dp

        /** The wide list's block is wider than the chips' and the narrow list's: its five columns need the room. */
        val MAX_WIDE_LIST_WIDTH: Dp = 600.dp

        /** The chips2 block's height at the top of its step: 280dp less its padding. Taller widgets centre it. */
        val MAX_CONTENT_HEIGHT: Dp = 252.dp

        /** The 48dp touch-target floor: the pill's height, and a list row's least. */
        val PILL_HEIGHT: Dp = 48.dp

        val SNOWFLAKE: Dp = 14.dp

        /** The content width from which the streak reads "N days in a row" on chips2. */
        val LONG_STREAK_FROM: Dp = 320.dp

        /** The widget width (5 cells and up) from which the narrow list has room for the New best mark. */
        val NEW_BEST_CHIP_FROM: Dp = 340.dp

        val LIST_HEADER_GAP: Dp = 2.dp
        val LIST_PILL_GAP: Dp = 4.dp
        val LIST_ROW_MIN: Dp = PILL_HEIGHT
        val LIST_ROW_MAX: Dp = 58.dp

        private val CHIPS1 = Look(
            horizontalPadding = 10.dp,
            verticalPadding = 8.dp,
            ring = 32.dp,
            streakText = 16.sp,
            flame = 18.dp,
            longStreak = false,
            timeLine = false,
            pill = false,
        )

        private val CHIPS2 = Look(
            horizontalPadding = 14.dp,
            verticalPadding = 14.dp,
            ring = 36.dp,
            streakText = 17.sp,
            flame = 19.dp,
            longStreak = true,
            timeLine = true,
            pill = true,
        )

        private val LIST_NARROW = CHIPS2.copy(verticalPadding = 10.dp, longStreak = false, timeLine = false)

        private val LIST_WIDE = LIST_NARROW.copy(ring = 40.dp, streakText = 20.sp, flame = 22.dp, longStreak = true)

        fun of(size: DpSize): WidgetLayout {
            val step = WidgetStep.of(size)
            val base = when (step) {
                WidgetStep.Chips1 -> CHIPS1
                WidgetStep.Chips2 -> CHIPS2
                WidgetStep.ListNarrow -> LIST_NARROW
                WidgetStep.ListWide -> LIST_WIDE
            }
            val maxWidth = if (step == WidgetStep.ListWide) MAX_WIDE_LIST_WIDTH else MAX_CONTENT_WIDTH
            val contentWidth = (size.width - base.horizontalPadding * 2).coerceAtMost(maxWidth)
            // "658 days in a row" plus the freezes and "5 of 5 done" do not fit the 4-cell content width; the short words do.
            val look = if (base.longStreak && contentWidth < LONG_STREAK_FROM) base.copy(longStreak = false) else base
            val room = size.height - look.verticalPadding * 2
            val isList = step == WidgetStep.ListNarrow || step == WidgetStep.ListWide
            val rowHeight = if (isList) rowHeight(room, look) else 0.dp
            return WidgetLayout(
                step = step,
                look = look,
                contentWidth = contentWidth,
                contentHeight = if (isList) listHeight(look, rowHeight).coerceAtMost(room) else room.coerceAtMost(MAX_CONTENT_HEIGHT),
                rowHeight = rowHeight,
                newBestChip = size.width >= NEW_BEST_CHIP_FROM,
            )
        }

        /** The room left for five rows once the header, the pill and their gaps are paid, shared out and kept between 48 and 58dp. */
        private fun rowHeight(room: Dp, look: Look): Dp {
            val fixed = look.streakHeight + LIST_HEADER_GAP + LIST_PILL_GAP + PILL_HEIGHT
            return floor(((room - fixed) / ROWS).value).dp.coerceIn(LIST_ROW_MIN, LIST_ROW_MAX)
        }

        private fun listHeight(look: Look, rowHeight: Dp): Dp =
            look.streakHeight + LIST_HEADER_GAP + rowHeight * ROWS + LIST_PILL_GAP + PILL_HEIGHT

        const val ROWS = 5
    }
}
