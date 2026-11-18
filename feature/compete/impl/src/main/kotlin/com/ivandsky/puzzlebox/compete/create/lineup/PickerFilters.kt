package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.lineup.Freshness
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource

/**
 * The chip groups of the exact-puzzle search, each a row of chips of one shape: New and Not played; where the
 * puzzle is from; and the games. Tapping a chip toggles it; the groups combine as the [query] says. For [pastYou] every
 * puzzle is one the player solved, so New, Not played, Community and Mine have nothing to tell apart: only Past days and From friends, then the games.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickerFilters(
    query: PickerQuery,
    games: List<DuelGame>,
    viewModel: AddPuzzleViewModel,
    modifier: Modifier = Modifier,
    pastYou: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (pastYou) {
            ChipRow {
                Chip("Past days", "pastdays", PuzzleSource.PastDay in query.sources) { viewModel.toggle(PuzzleSource.PastDay) }
                Chip("From friends", "friends", PuzzleSource.Friend in query.sources) { viewModel.toggle(PuzzleSource.Friend) }
            }
        } else {
            ChipRow {
                Chip("New", "new", Freshness.New in query.fresh) { viewModel.toggle(Freshness.New) }
                Chip("Not played", "notplayed", Freshness.NotPlayed in query.fresh) { viewModel.toggle(Freshness.NotPlayed) }
            }
            ChipRow {
                Chip("Community", "community", PuzzleSource.Community in query.sources) { viewModel.toggle(PuzzleSource.Community) }
                Chip("From friends", "friends", PuzzleSource.Friend in query.sources) { viewModel.toggle(PuzzleSource.Friend) }
                Chip("Past days", "pastdays", PuzzleSource.PastDay in query.sources) { viewModel.toggle(PuzzleSource.PastDay) }
                Chip("Mine", "mine", PuzzleSource.Mine in query.sources) { viewModel.toggle(PuzzleSource.Mine) }
            }
        }
        ChipRow {
            games.forEach { game -> Chip(game.name, "game.${game.id.value}", game.id in query.games) { viewModel.toggle(game.id) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
}

@Composable
private fun Chip(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.testTag("duel.lineup.chip.$tag"))
}
