package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The sizes the puzzles table is built on. The header and every row read the same ones, so each player's time column
 * ends at the same x in all of them: that is what lines the times and ticks up. [timeWidth] is one player's column;
 * [compact] is the phone, where the margin bar sits beside the times on a second line under the puzzle's name.
 */
internal class TableMetrics(val compact: Boolean, val timeWidth: Dp) {
    val columnGap: Dp = if (compact) Spacing.sm else Spacing.lg
    val sidePadding: Dp = if (compact) Spacing.md else Spacing.xl
    val marginLabelWidth: Dp = if (compact) 52.dp else 64.dp

    companion object {
        private val PhoneTime = 100.dp
        private val TabletTime = 188.dp
        private val NarrowTabletTime = 148.dp
        private val RoomyTable = 960.dp

        /** The metrics for a table [tableWidth] wide; a narrow tablet gets slimmer time columns so the name keeps its room. */
        fun of(compact: Boolean, tableWidth: Dp) = TableMetrics(
            compact,
            when {
                compact -> PhoneTime
                tableWidth >= RoomyTable -> TabletTime
                else -> NarrowTabletTime
            },
        )
    }
}
