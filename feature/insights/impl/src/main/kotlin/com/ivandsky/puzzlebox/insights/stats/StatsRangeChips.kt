package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.FilterChipRow
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange

/** The range chips, one choice at a time: [selected] drawn picked, a tap reporting its range through [onSelect] (A10). */
@Composable
internal fun StatsRangeChips(selected: StatsRange, onSelect: (StatsRange) -> Unit, modifier: Modifier = Modifier) {
    FilterChipRow(modifier.testTag("stats.range")) {
        StatsRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(range.label()) },
                modifier = Modifier.semantics { role = Role.RadioButton }.testTag("stats.range.${range.name}"),
            )
        }
    }
}

/** The range as its chip and the page read it. */
internal fun StatsRange.label(): String = when (this) {
    StatsRange.Week -> "Week"
    StatsRange.Month -> "Month"
    StatsRange.All -> "All"
}
