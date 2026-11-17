package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The sizes the puzzles table is built on. The header and every row read the same ones, so each player's time column
 * ends at the same x in all of them: that is what lines the times and ticks up. [timeWidth] is one player's column;
 * [compact] is the phone, where the margin bar sits beside the times on a second line under the puzzle's name. A self
 * duel's phone table ([pastYou]) takes narrower time columns and label so the margin track keeps a readable length.
 */
internal class TableMetrics(val compact: Boolean, val timeWidth: Dp, pastYou: Boolean = false) {
    val columnGap: Dp = if (compact) Spacing.sm else Spacing.lg
    val sidePadding: Dp = if (compact) Spacing.md else Spacing.xl
    val marginLabelWidth: Dp = if (compact) (if (pastYou) 40.dp else 52.dp) else 64.dp

    companion object {
        private val PhoneTime = 100.dp
        private val PastYouPhoneTime = 84.dp
        private val TabletTime = 188.dp
        private val NarrowTabletTime = 148.dp
        private val RoomyTable = 960.dp

        /** The metrics for a table [tableWidth] wide; a narrow tablet gets slimmer time columns so the name keeps its room. */
        fun of(compact: Boolean, tableWidth: Dp, pastYou: Boolean = false) = TableMetrics(
            compact,
            when {
                compact && pastYou -> PastYouPhoneTime
                compact -> PhoneTime
                tableWidth >= RoomyTable -> TabletTime
                else -> NarrowTabletTime
            },
            pastYou,
        )
    }
}
