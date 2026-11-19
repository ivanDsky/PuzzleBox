package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.FilterChipRow
import com.ivandsky.puzzlebox.designsystem.SingleFilterChip
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.resultui.gameFilterOption
import com.ivandsky.puzzlebox.resultui.levelFilterOption

private val DifficultyOptions = DifficultyChoice.entries.map {
    levelFilterOption(it, it.label, it.mark, tag = "community.filter.difficulty.${it.name.lowercase()}")
}

/** Game and Difficulty as single-pick chips; each reads and writes [state] through [actions]. */
@Composable
internal fun CommunityFilterChips(state: CommunityState, actions: CommunityActions, modifier: Modifier = Modifier) {
    val gameOptions = remember(state.games) {
        state.games.map { gameFilterOption(it.id, it.name, it.brand, tag = "community.filter.game.${it.id.value}") }
    }
    FilterChipRow(modifier) {
        SingleFilterChip(
            name = "Game",
            allLabel = "All games",
            options = gameOptions,
            selected = state.game,
            onSelect = actions.onGame,
            tag = "community.filter.game",
            allTag = "community.filter.game.all",
        )
        SingleFilterChip(
            name = "Difficulty",
            allLabel = "Any difficulty",
            options = DifficultyOptions,
            selected = state.difficulty,
            onSelect = actions.onDifficulty,
            tag = "community.filter.difficulty",
            allTag = "community.filter.difficulty.any",
        )
    }
}

/** The head of the list: the chips, then the count while the list has rows. As a list item so the phone page and the tablet section share it. */
internal fun LazyListScope.communityFilterHead(state: CommunityState, actions: CommunityActions) {
    item(key = "community:chips", contentType = "community.filter") {
        CommunityFilterChips(state, actions, Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
    }
    val count = (state.phase as? CommunityPhase.Loaded)?.rows?.size ?: 0
    if (count > 0) {
        item(key = "community:count", contentType = "community.count") {
            Text(
                if (count == 1) "1 puzzle" else "$count puzzles",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm).testTag("community.filter.count"),
            )
        }
    }
}
