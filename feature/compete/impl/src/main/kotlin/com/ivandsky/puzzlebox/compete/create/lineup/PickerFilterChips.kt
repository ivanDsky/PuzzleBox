package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.FilterChipRow
import com.ivandsky.puzzlebox.designsystem.MultiFilterChip

/** The picker's three "Pick several" chips and Clear. Past you has Source (Past days, From friends) and Games only. */
@Composable
internal fun PickerFilterChips(
    state: AddPuzzleState,
    games: List<DuelGame>,
    viewModel: AddPuzzleViewModel,
    modifier: Modifier = Modifier,
) {
    val query = state.query
    val freshness = PickerFilterPage.Freshness
    val source = PickerFilterPage.Source
    val gamesPage = PickerFilterPage.Games
    FilterChipRow(modifier) {
        if (!state.pastYou) {
            MultiFilterChip(
                freshness.title, freshness.allLabel, remember { freshnessOptions() }, query.fresh,
                onToggle = { viewModel.toggle(it) }, onClearGroup = viewModel::clearFreshness,
                tag = freshness.tag, allTag = freshness.allTag,
            )
        }
        MultiFilterChip(
            source.title, source.allLabel, remember(state.pastYou) { sourceOptions(state.pastYou) }, query.sources,
            onToggle = { viewModel.toggle(it) }, onClearGroup = viewModel::clearSources,
            tag = source.tag, allTag = source.allTag,
        )
        MultiFilterChip(
            gamesPage.title, gamesPage.allLabel, gameOptions(games), query.games,
            onToggle = { viewModel.toggle(it) }, onClearGroup = viewModel::clearGames,
            tag = gamesPage.tag, allTag = gamesPage.allTag,
        )
        if (state.activeFilters > 0) AppTextButton("Clear", onClick = viewModel::clearFilters, modifier = Modifier.testTag("duel.lineup.filter.clear"))
    }
}
