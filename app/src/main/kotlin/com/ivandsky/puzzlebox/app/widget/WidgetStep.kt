package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The widget's four layouts, chosen from the size the launcher reports: height first, then width. The thresholds describe
 * content in dp, not grid cells, so a launcher that reports a few dp off still lands on the right step.
 */
internal enum class WidgetStep {
    /** One row of chips under the streak line, no button. */
    Chips1,

    /** Chips with rank over time, and the action pill. */
    Chips2,

    /** The list, for a tall widget under [WIDE_FROM] wide. */
    ListNarrow,

    /** The list with its columns, for a tall widget at least [WIDE_FROM] wide. */
    ListWide;

    companion object {
        val CHIPS2_FROM_HEIGHT: Dp = 160.dp
        val LIST_FROM_HEIGHT: Dp = 280.dp
        val WIDE_FROM: Dp = 480.dp

        fun of(size: DpSize): WidgetStep = when {
            size.height < CHIPS2_FROM_HEIGHT -> Chips1
            size.height < LIST_FROM_HEIGHT -> Chips2
            size.width < WIDE_FROM -> ListNarrow
            else -> ListWide
        }
    }
}
