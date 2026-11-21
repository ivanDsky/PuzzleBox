package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * What the widget draws with at the size the launcher gave it ([of]; under a responsive size mode the launcher's size is the
 * largest declared size that fits, see [WidgetSizes]): the [step], its [look], and the block ([contentWidth] by [contentHeight])
 * the widget centres. Sizes do not follow the widget, except the chips2 ring, which grows with the room it has, and the list
 * [rowHeight]; past the block's largest size the extra space is background around it. [newBestChip] is whether the narrow
 * list has room for the New best mark.
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
     * The sizes of a step: the streak line or header, the ring, whether the chips have a second line, the step has an action
     * pill and the chips have a [streakLine] above them, and the chip's text sizes.
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
        val streakLine: Boolean = true,
        val chipFirstText: TextUnit = 14.sp,
        val chipSecondText: TextUnit = 12.sp,
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

        /** The chips2 ring's range; the least is the phone's. */
        val RING_MIN: Dp = 36.dp
        val RING_MAX: Dp = 56.dp

        /** Above this ring the chip text is the larger size. */
        val RING_LARGE_TEXT_ABOVE: Dp = 44.dp

        /** The room a chip keeps on each side of its ring for its text, so a long time still sits in the chip's share. */
        val CHIP_SIDE_ROOM: Dp = 24.dp

        /** The least gap above and below the chips, which the rest of the height is shared between. */
        val CHIP_GAP_MIN: Dp = 6.dp

        val CHIP_TEXT_GAP: Dp = 2.dp
        val CHIP_FIRST_LARGE: TextUnit = 16.sp
        val CHIP_SECOND_LARGE: TextUnit = 13.sp

        /** The least vertical padding a one-row chips1 keeps, so its ring is not on the widget's edge. */
        private val CHIPS1_PADDING_MIN: Dp = 2.dp
        private const val LINE_HEIGHT_RATIO = 1.2f

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
                WidgetStep.Chips1 -> fitChips1(size.height)
                WidgetStep.Chips2 -> CHIPS2
                WidgetStep.ListNarrow -> LIST_NARROW
                WidgetStep.ListWide -> LIST_WIDE
            }
            val maxWidth = if (step == WidgetStep.ListWide) MAX_WIDE_LIST_WIDTH else MAX_CONTENT_WIDTH
            val contentWidth = (size.width - base.horizontalPadding * 2).coerceAtMost(maxWidth)
            // "658 days in a row" plus the freezes and "5 of 5 done" do not fit the 4-cell content width; the short words do.
            val baseLook = if (base.longStreak && contentWidth < LONG_STREAK_FROM) base.copy(longStreak = false) else base
            val room = size.height - baseLook.verticalPadding * 2
            val isList = step == WidgetStep.ListNarrow || step == WidgetStep.ListWide
            val rowHeight = if (isList) rowHeight(room, baseLook) else 0.dp
            val contentHeight = if (isList) listHeight(baseLook, rowHeight).coerceAtMost(room) else room.coerceAtMost(MAX_CONTENT_HEIGHT)
            return WidgetLayout(
                step = step,
                look = if (step == WidgetStep.Chips2) withChipsRing(baseLook, contentWidth, contentHeight) else baseLook,
                contentWidth = contentWidth,
                contentHeight = contentHeight,
                rowHeight = rowHeight,
                newBestChip = size.width >= NEW_BEST_CHIP_FROM,
            )
        }

        /**
         * Chips1 in the height it was given. A one-row widget is short, so what does not fit goes in this order: the vertical
         * padding shrinks, from 8dp to 2dp at the least, then the streak line goes and the chips stay with the padding they can
         * have. The five chips and their taps are never dropped.
         */
        private fun fitChips1(height: Dp): Look {
            val chips = chipsRowHeight(CHIPS1)
            val withStreak = chips + CHIPS1.streakHeight

            fun padding(block: Dp) = floor(((height - block) / 2).value).dp.coerceIn(CHIPS1_PADDING_MIN, CHIPS1.verticalPadding)
            val streakPadding = padding(withStreak)
            if (height - streakPadding * 2 >= withStreak) return CHIPS1.copy(verticalPadding = streakPadding)
            return CHIPS1.copy(verticalPadding = padding(chips), streakLine = false)
        }

        /** The room left for five rows once the header, the pill and their gaps are paid, shared out and kept between 48 and 58dp. */
        private fun rowHeight(room: Dp, look: Look): Dp {
            val fixed = look.streakHeight + LIST_HEADER_GAP + LIST_PILL_GAP + PILL_HEIGHT
            return floor(((room - fixed) / ROWS).value).dp.coerceIn(LIST_ROW_MIN, LIST_ROW_MAX)
        }

        private fun listHeight(look: Look, rowHeight: Dp): Dp =
            look.streakHeight + LIST_HEADER_GAP + rowHeight * ROWS + LIST_PILL_GAP + PILL_HEIGHT

        /**
         * The chips2 ring: the smaller of what one chip's share of the width leaves once the text has its side room and what the
         * height leaves once the streak line, the pill, the chip text and the two least gaps are paid, between 36 and 56dp. Above
         * 44dp the chip's text steps up a size, which costs height, so the ring is worked out with the larger text first and
         * falls back to the small text, and a ring of 44dp at most, when the larger one would not make 44dp.
         */
        private fun withChipsRing(look: Look, contentWidth: Dp, contentHeight: Dp): Look {
            val widthBudget = contentWidth / SLOTS - CHIP_SIDE_ROOM
            val rest = contentHeight - look.streakHeight - PILL_HEIGHT - CHIP_GAP_MIN * 2
            val large = look.copy(chipFirstText = CHIP_FIRST_LARGE, chipSecondText = CHIP_SECOND_LARGE)
            val largeRing = minOf(widthBudget, rest - chipTextHeight(large)).wholeDp().coerceAtMost(RING_MAX)
            if (largeRing > RING_LARGE_TEXT_ABOVE) return large.copy(ring = largeRing)
            return look.copy(ring = minOf(widthBudget, rest - chipTextHeight(look)).wholeDp().coerceIn(RING_MIN, RING_LARGE_TEXT_ABOVE))
        }

        /** A chip's text under its ring: two lines and the 2dp above each. */
        fun chipTextHeight(look: Look): Dp = CHIP_TEXT_GAP * 2 + lineHeight(look.chipFirstText) + lineHeight(look.chipSecondText)

        /** A chip row: the ring and the text lines under it that the look has. */
        fun chipsRowHeight(look: Look): Dp =
            look.ring + if (look.timeLine) chipTextHeight(look) else CHIP_TEXT_GAP + lineHeight(look.chipFirstText)

        private fun lineHeight(text: TextUnit): Dp = ceil(text.value * LINE_HEIGHT_RATIO).dp

        private fun Dp.wholeDp(): Dp = floor(value).dp

        const val SLOTS = 5

        const val ROWS = 5
    }
}
