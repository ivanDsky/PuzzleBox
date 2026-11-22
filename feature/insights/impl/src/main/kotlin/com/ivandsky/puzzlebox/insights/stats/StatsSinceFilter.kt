package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.ivandsky.puzzlebox.designsystem.FilterOption
import com.ivandsky.puzzlebox.designsystem.SingleFilterSheet
import com.ivandsky.puzzlebox.designsystem.community.TextDropdownFilter
import com.ivandsky.puzzlebox.designsystem.community.TextDropdownFilterDefaults
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSince

/**
 * The "Since {date}" dropdown for [range], worded from [since] (A2). A tap opens the Since sheet: a row per range with
 * its sub-line, no "all" row, and a note that records count all time. A row is reported through [onSelect]. [quiet] is
 * the tablet's look, medium-weight muted text atop the pane.
 */
@Composable
internal fun StatsSinceFilter(
    range: StatsRange,
    since: StatsSince?,
    onSelect: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextDropdownFilter(
        label = range.sinceTitle(since),
        value = null,
        open = open,
        onClick = { open = true },
        modifier = modifier.testTag(SINCE_TAG),
        // Since always holds a value, so on the phone it is drawn as one: onSurface, semi-bold; only "Any game" reads muted.
        colors = if (quiet) TextDropdownFilterDefaults.quietColors() else TextDropdownFilterDefaults.colors(contentColor = MaterialTheme.colorScheme.onSurface),
        textStyle = if (quiet) TextDropdownFilterDefaults.quietTextStyle() else TextDropdownFilterDefaults.textStyle().copy(fontWeight = FontWeight.SemiBold),
    )
    if (open) {
        val options = remember(since) {
            StatsRange.entries.map { FilterOption(it, it.sinceTitle(since), tag = "$SINCE_TAG.${it.name}", supporting = it.sinceSubLine(since)) }
        }
        SingleFilterSheet(
            title = "Since",
            allLabel = null,
            options = options,
            selected = range,
            onSelect = { picked -> picked?.let(onSelect) },
            onDismiss = { open = false },
            tag = SINCE_TAG,
            footer = "Records always count all time.",
        )
    }
}

internal const val SINCE_TAG = "stats.filter.since"
