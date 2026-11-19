package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.designsystem.MultiFilterPage

/** The three option groups in chip order. The host that holds one is showing that group's page. Past you has no Freshness. */
internal enum class PickerFilterPage(val title: String, val allLabel: String, val tag: String) {
    Freshness("Freshness", "Any freshness", "duel.lineup.filter.freshness"),
    Source("Source", "All sources", "duel.lineup.filter.source"),
    Games("Games", "All games", "duel.lineup.filter.games"),
    ;

    val allTag: String get() = "$tag.all"
}

/** The page of [page]: a MultiFilterPage over the options, toggles and Clear-group calls the matching chip makes. Tags `duel.lineup.filter.<group>.page` and `….page.back`. */
@Composable
internal fun PickerFilterPageContent(
    page: PickerFilterPage,
    state: AddPuzzleState,
    games: List<DuelGame>,
    viewModel: AddPuzzleViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (page) {
        PickerFilterPage.Freshness -> MultiFilterPage(
            page.title, page.allLabel, freshnessOptions(), state.query.fresh,
            onToggle = { viewModel.toggle(it) }, onClearGroup = viewModel::clearFreshness, onBack = onBack, modifier = modifier, tag = page.tag, allTag = page.allTag,
        )
        PickerFilterPage.Source -> MultiFilterPage(
            page.title, page.allLabel, sourceOptions(state.pastYou), state.query.sources,
            onToggle = { viewModel.toggle(it) }, onClearGroup = viewModel::clearSources, onBack = onBack, modifier = modifier, tag = page.tag, allTag = page.allTag,
        )
        PickerFilterPage.Games -> MultiFilterPage(
            page.title, page.allLabel, gameOptions(games), state.query.games,
            onToggle = { viewModel.toggle(it) }, onClearGroup = viewModel::clearGames, onBack = onBack, modifier = modifier, tag = page.tag, allTag = page.allTag,
        )
    }
}
