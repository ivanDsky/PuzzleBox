package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.model.GameId

/** The page under the bar: the [filters] (the range chips by default), then the sections for [state], or a quiet message while there are none. */
@Composable
internal fun StatsContent(
    state: StatsUiState,
    gameName: (GameId) -> String,
    onSelectRange: (StatsRange) -> Unit,
    onOpenSolve: (SolveRef) -> Unit,
    modifier: Modifier = Modifier,
    filters: @Composable () -> Unit = { StatsRangeChips(state.range, onSelectRange, Modifier.padding(horizontal = Spacing.lg)) },
) {
    Column(modifier.fillMaxSize().maxContentWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        filters()
        when (val body = state.body) {
            StatsBody.Loading -> Box(Modifier.fillMaxSize())
            StatsBody.Restoring -> EmptyState(
                title = "Bringing back your history…",
                message = "Your stats will show up here in a moment.",
                modifier = Modifier.fillMaxSize().testTag("stats.restoring"),
            )
            StatsBody.Empty -> EmptyState(
                title = "Nothing here yet",
                message = "No solved puzzles in this range yet.",
                modifier = Modifier.fillMaxSize().testTag("stats.empty"),
            )
            is StatsBody.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("stats.sections"),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                itemsIndexed(body.sections, key = { _, section -> section::class.qualifiedName ?: section.toString() }) { _, section -> StatsSectionSlot(section, gameName, onOpenSolve, singleGame = state.game != null) }
            }
        }
    }
}
