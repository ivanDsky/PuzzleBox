package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * What the widget draws with at the size the launcher gave it ([of]): the [step] and its [look]. Under a responsive size mode
 * that size is the largest declared size that fits ([WidgetSizes]), while the launcher lays the views out at the widget's real,
 * larger size. So the look (the step, the ring, the text, the row height) is chosen by the size it is composed at, and the room
 * the widget really has is filled by weights, not by these numbers. [contentHeight] is the block's height at the composed size,
 * and the list's [rowHeight] its least, 48dp, stepped up with the [Look.scale] of a big widget. When that block is shorter than
 * the list's header, five rows and pill ([fixedRows], the 4x3 sizes), the rows keep [rowHeight] and the block its height,
 * centred; otherwise the rows share the height.
 */
internal data class WidgetLayout(
    val step: WidgetStep,
    val look: Look,
    val contentHeight: Dp,
    val rowHeight: Dp,
    val fixedRows: Boolean,
) {
    /**
     * The sizes of a step: the streak line or header, the ring, whether the chips have a second line, the step has an action
     * pill, whether the streak line says how many games are [doneCount], the chip's text sizes and the list's [scale].
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
        val doneCount: Boolean = true,
        val scale: Float = 1f,
        val chipFirstText: TextUnit = 14.sp,
        val chipSecondText: TextUnit = 12.sp,
    ) {
        /** The streak line's height: the flame and a little air. */
        val streakHeight: Dp get() = flame + 4.dp

        /** A list text size of [base] sp, stepped up with the [scale] to a whole sp. */
        fun text(base: Int): TextUnit = stepped(base.toFloat()).sp

        /** A list size of [base], stepped up with the [scale] to a whole dp. */
        fun size(base: Dp): Dp = stepped(base.value).dp

        private fun stepped(value: Float): Int = (value * scale).roundToInt()
    }

    companion object {
        /** The 48dp touch-target floor: the pill's height, and a list row's least. */
        val PILL_HEIGHT: Dp = 48.dp

        val SNOWFLAKE: Dp = 14.dp

        /** The content width from which the streak reads "N days in a row" on chips2. */
        val LONG_STREAK_FROM: Dp = 320.dp

        /** Under this widget width, 3 cells, chips2 trades its padding and the done count for the room its five chips need. */
        val CHIPS2_NARROW_UNDER: Dp = 250.dp

        val LIST_HEADER_GAP: Dp = 2.dp
        val LIST_PILL_GAP: Dp = 4.dp
        val LIST_ROW_MIN: Dp = PILL_HEIGHT

        /** The wide list steps up one size from this widget size (5 by 4 cells on the tablet's grid) and another from [STEP_UP_2]. */
        val STEP_UP_1: DpSize = DpSize(560.dp, 400.dp)
        val STEP_UP_2: DpSize = DpSize(700.dp, 500.dp)
        const val SCALE_1 = 1.15f
        const val SCALE_2 = 1.3f

        /** The chip ring's range; the least is the phone's. */
        val RING_MIN: Dp = 36.dp
        val RING_MAX: Dp = 56.dp

        /** Above this chips2 ring, and from it a chips1 ring, the chip text is the larger size. */
        val RING_LARGE_TEXT_ABOVE: Dp = 44.dp

        /** The room a chips2 chip keeps on each side of its ring for its text, so a long time still sits in the chip's share. */
        val CHIP_SIDE_ROOM: Dp = 24.dp

        /** The room a chips1 ring leaves its slice of the width: 2dp each side. */
        val CHIPS1_SLICE_GAP: Dp = 4.dp

        /** The least gap above and below the chips, which the rest of the height is shared between. */
        val CHIP_GAP_MIN: Dp = 6.dp

        val CHIP_TEXT_GAP: Dp = 2.dp
        val CHIP_FIRST_LARGE: TextUnit = 16.sp
        val CHIP_SECOND_LARGE: TextUnit = 13.sp

        private const val LINE_HEIGHT_RATIO = 1.2f

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

        // Edge to edge, so a chip is the whole of its fifth of the widget and its tap.
        private val CHIPS1 = CHIPS2.copy(horizontalPadding = 0.dp, verticalPadding = 0.dp, longStreak = false, timeLine = false, pill = false)

        private val LIST_NARROW = CHIPS2.copy(verticalPadding = 10.dp, longStreak = false, timeLine = false)

        private val LIST_WIDE = LIST_NARROW.copy(ring = 40.dp, streakText = 20.sp, flame = 22.dp, longStreak = true)

        fun of(size: DpSize): WidgetLayout {
            val step = WidgetStep.of(size)
            val base = when (step) {
                WidgetStep.Chips1 -> chips1(size)
                WidgetStep.Chips2 -> if (size.width < CHIPS2_NARROW_UNDER) CHIPS2.copy(horizontalPadding = 8.dp, doneCount = false) else CHIPS2
                WidgetStep.ListNarrow -> LIST_NARROW
                WidgetStep.ListWide -> listWide(size)
            }
            val contentWidth = size.width - base.horizontalPadding * 2
            // "658 days in a row" plus the freezes and "5 of 5 done" do not fit the 4-cell content width; the short words do.
            val look = if (base.longStreak && contentWidth < LONG_STREAK_FROM) base.copy(longStreak = false) else base
            val contentHeight = size.height - look.verticalPadding * 2
            val isList = step == WidgetStep.ListNarrow || step == WidgetStep.ListWide
            val rowHeight = if (isList) look.size(LIST_ROW_MIN) else 0.dp
            return WidgetLayout(
                step = step,
                look = if (step == WidgetStep.Chips2) withChipsRing(look, contentWidth, contentHeight) else look,
                contentHeight = contentHeight,
                rowHeight = rowHeight,
                fixedRows = isList && contentHeight < listHeight(look, rowHeight),
            )
        }

        /**
         * Chips1: the ring over one result line, in the height and width of the size. The ring is the smaller of what a fifth of the
         * width leaves and what the height leaves once the line and its air are paid, between 36 and 56dp, so it grows with the row
         * from 36dp at 56dp tall. From a 44dp ring the line is the larger size, which costs height, so the ring is worked out with
         * the larger text first and falls back to the small text, and a ring under 44dp, when the larger one would not make 44dp.
         */
        private fun chips1(size: DpSize): Look {
            val slice = floor(size.width.value / SLOTS).dp - CHIPS1_SLICE_GAP
            val large = CHIPS1.copy(chipFirstText = CHIP_FIRST_LARGE)
            val largeRing = minOf(slice, size.height - chips1Text(large)).wholeDp().coerceAtMost(RING_MAX)
            if (largeRing >= RING_LARGE_TEXT_ABOVE) return large.copy(ring = largeRing)
            val smallRing = minOf(slice, size.height - chips1Text(CHIPS1)).wholeDp()
            return CHIPS1.copy(ring = smallRing.coerceIn(RING_MIN, RING_LARGE_TEXT_ABOVE - 1.dp))
        }

        /** What a chips1 chip spends under its ring: the gap above the line, the line and the same gap's worth of air below. */
        private fun chips1Text(look: Look): Dp = CHIP_TEXT_GAP * 2 + lineHeight(look.chipFirstText)

        /** The wide list at the size, one size up from [STEP_UP_1] and two from [STEP_UP_2]; each step costs rows and text a size. */
        private fun listWide(size: DpSize): Look {
            val scale = when {
                size.width >= STEP_UP_2.width && size.height >= STEP_UP_2.height -> SCALE_2
                size.width >= STEP_UP_1.width && size.height >= STEP_UP_1.height -> SCALE_1
                else -> return LIST_WIDE
            }
            val stepped = LIST_WIDE.copy(scale = scale)
            return stepped.copy(ring = stepped.size(LIST_WIDE.ring), streakText = stepped.text(LIST_WIDE.streakText.value.toInt()), flame = stepped.size(LIST_WIDE.flame))
        }

        /** The list's header, five rows at [rowHeight], the pill and their gaps. */
        fun listHeight(look: Look, rowHeight: Dp): Dp =
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
