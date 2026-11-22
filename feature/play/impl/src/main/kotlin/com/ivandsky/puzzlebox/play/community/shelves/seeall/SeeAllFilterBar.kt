package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.community.SortMenuButton

private val BarHeight = 52.dp

/**
 * The page's filters as plain text dropdowns over a hairline: Any game (not on a game's own page), Any difficulty, and the
 * sort menu at the end. Each reads [state] and reports through [actions].
 */
@Composable
internal fun SeeAllFilterBar(state: SeeAllUiState, actions: SeeAllActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("seeall.filters")) {
        Row(
            Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.showGameFilter) SeeAllGameFilter(state.game, state.games, actions.onGame)
            SeeAllDifficultyFilter(state.difficulty, actions.onDifficulty)
            Spacer(Modifier.weight(1f))
            SortControl(state, actions)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SortControl(state: SeeAllUiState, actions: SeeAllActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    SortMenuButton(
        options = state.sortOptions,
        selected = state.sort,
        optionLabel = SeeAllSort::label,
        expanded = open,
        onExpandedChange = { open = it },
        onSelect = actions.onSort,
        modifier = Modifier.testTag("seeall.sort"),
    )
}
