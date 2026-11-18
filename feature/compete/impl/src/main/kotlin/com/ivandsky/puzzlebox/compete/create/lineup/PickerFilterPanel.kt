package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.compete.create.CustomizeBody
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The [PickerFilters] chips and a Clear filters action, growing in under the search field when [state] says the panel is
 * open. Clear is off while no chip is on, so the panel does not change height as chips toggle.
 */
@Composable
internal fun PickerFilterPanel(state: AddPuzzleState, games: List<DuelGame>, viewModel: AddPuzzleViewModel, modifier: Modifier = Modifier) {
    CustomizeBody(state.filtersOpen) {
        Column(modifier.padding(top = Spacing.sm).testTag("duel.lineup.filter.panel"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PickerFilters(state.query, games, viewModel, pastYou = state.pastYou)
            TextButton(onClick = viewModel::clearFilters, enabled = state.activeFilters > 0, modifier = Modifier.testTag("duel.lineup.filter.clear")) {
                Text("Clear filters")
            }
        }
    }
}
