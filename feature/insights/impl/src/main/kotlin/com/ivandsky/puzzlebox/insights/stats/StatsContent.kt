package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.stats.grouped.StatsGroupedList
import com.ivandsky.puzzlebox.model.GameId

/** The page under the bar: the [filters], then the sections for [state] in their groups, or a quiet message while there are none. */
@Composable
internal fun StatsContent(
    state: StatsUiState,
    gameName: (GameId) -> String,
    onOpenSolve: (SolveRef) -> Unit,
    modifier: Modifier = Modifier,
    filters: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize().groupedMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            is StatsBody.Ready -> StatsGroupedList(body.sections, state.game, gameName, onOpenSolve)
        }
    }
}

/** The page's width cap: past [MaxContentWidth] so a wide pane gives the side column room, still centred. */
private fun Modifier.groupedMaxWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = GroupedMaxWidth).fillMaxWidth()

private val GroupedMaxWidth = 960.dp
