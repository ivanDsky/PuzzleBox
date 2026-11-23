package com.ivandsky.puzzlebox.insights.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.TwoColumnFrame
import com.ivandsky.puzzlebox.designsystem.TwoColumnMinWidth
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.domain.recap.SummarizedPeriod

/**
 * The hub's tiles. On a phone a tile opens its recap. On a tablet (a pane wide enough for [TwoColumnFrame]) a tile
 * selects its period, outlined, and the cover pane beside the grid opens the selected one; the newest period is
 * selected until another is chosen.
 */
@Composable
internal fun HubReady(state: InsightsHubUiState.Ready, onOpen: (RecapPeriod) -> Unit, onOpenStats: () -> Unit, topBar: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < TwoColumnMinWidth) {
            Column(Modifier.fillMaxSize()) {
                topBar()
                HubGrid(state, selectedId = null, onTile = { onOpen(it.period) }, onOpenStats, Modifier.weight(1f))
            }
            return@BoxWithConstraints
        }
        var chosen by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = state.periods.firstOrNull { it.period.id == chosen } ?: state.periods.first()
        val paneHeight = maxHeight
        TwoColumnFrame(
            paneHeight = paneHeight,
            tag = "insights.hub.twoPane",
            contentMaxWidth = HubMaxContentWidth,
            left = { column, height ->
                Column(column.height(height)) {
                    topBar()
                    HubGrid(state, selected.period.id, onTile = { chosen = it.period.id }, onOpenStats, Modifier.weight(1f))
                }
            },
            right = {
                HubCoverPane(
                    selected,
                    opened = selected.period.id in state.opened,
                    yearSoFar = state.yearSoFar,
                    onOpen = { onOpen(selected.period) },
                    modifier = Modifier.height(paneHeight - Spacing.lg * 2),
                )
            },
        )
    }
}

@Composable
private fun HubGrid(
    state: InsightsHubUiState.Ready,
    selectedId: String?,
    onTile: (SummarizedPeriod) -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier,
) {
    val years = state.periods.filter { it.period is RecapPeriod.Year }
    val months = state.periods.filter { it.period is RecapPeriod.Month }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TileColumn),
        modifier = modifier.testTag("insights.hub.list"),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "stats", span = FullSpan) { StatsRow(onOpenStats) }
        section("YEARS", years, state, selectedId, onTile, firstIndex = 0)
        section("MONTHS", months, state, selectedId, onTile, firstIndex = years.size)
    }
}

/** A full-width [label] over [items]' tiles, none at all when [items] is empty. [firstIndex] is the first tile's place in the grid. */
private fun LazyGridScope.section(
    label: String,
    items: List<SummarizedPeriod>,
    state: InsightsHubUiState.Ready,
    selectedId: String?,
    onTile: (SummarizedPeriod) -> Unit,
    firstIndex: Int,
) {
    if (items.isEmpty()) return
    item(key = label, span = FullSpan) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
    }
    items(items.size, key = { items[it].period.id }, span = { GridItemSpan(if (items[it].period is RecapPeriod.Year) maxLineSpan else 1) }) { i ->
        val item = items[i]
        val enters = entersWithGrid(firstIndex + i)
        val beat = if (enters) Modifier.resultBeat(ResultBeats.Glyph, firstIndex + i) else Modifier
        HubCoverTile(
            item,
            opened = item.period.id in state.opened,
            yearSoFar = state.yearSoFar,
            selected = item.period.id == selectedId,
            onClick = { onTile(item) },
            modifier = beat,
            entry = enters,
            // A phone tile opens its story, so the story grows out of it; a tablet tile only selects (the pane opens).
            liftKey = if (selectedId == null) InsightsRecapKey(item.period.id) else null,
        )
    }
}

private val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** The "Stats for all games" row: a plate with the bars icon, the words and a chevron. */
@Composable
private fun StatsRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg)
            .height(StatsRowHeight)
            .testTag("insights.hub.stats"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PuzzleIcons.LevelHard, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Spacing.xl))
        Text("Stats for all games", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(PuzzleIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The widest the tablet's two columns grow: the reference's 652dp of tiles, gap and 372dp pane; a wider screen centers them. */
private val HubMaxContentWidth = 1080.dp
private val TileColumn = 170.dp
private val StatsRowHeight = 56.dp
private val RowRadius = 20.dp

/** How many of the grid's first tiles enter with motion: the first screenful, which the eye is on when the hub opens. */
internal const val ENTERING_TILES = 5

/** True for the tile at [index] in the grid's order when it plays its entry motion; a tile scrolled in later is static. */
internal fun entersWithGrid(index: Int): Boolean = index < ENTERING_TILES
