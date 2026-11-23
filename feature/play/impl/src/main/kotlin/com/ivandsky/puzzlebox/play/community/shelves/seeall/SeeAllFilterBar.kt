package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

private val BarHeight = 52.dp

/**
 * The page's filters as plain text dropdowns over a hairline: Any game (not on a game's own page) and Any difficulty.
 * Each reads [state] and reports through [actions]. There is no sort: every page is newest first.
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
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
