package com.ivandsky.puzzlebox.compete.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.compete.create.lineup.LineupPanel
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The panel for a duel against a friend: the Quick, Daily and Customize presets, the footer and Create duel. */
@Composable
internal fun FriendCreatePanel(state: NewDuelState, actions: NewDuelActions) {
    var customizing by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        state.invitee?.let { name ->
            Text(
                text = "Inviting $name",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("duel.invitee"),
            )
        }
        PresetCard("Quick duel", "Best of 3 · mixed past dailies · 24 h", draft.preset == DuelPreset.Quick, actions.onQuick, "duel.preset.quick")
        PresetCard("Daily duel", "Today's puzzles · 24 h", draft.preset == DuelPreset.Daily, actions.onDaily, "duel.preset.daily") {
            if (draft.preset == DuelPreset.Daily) {
                if (draft.showsRows(state.lineup, state.games)) {
                    LineupPanel(state.lineup, state.games, draft.includeFriends, actions.onEditLineup, fixed = true)
                } else {
                    DailyGames(state.games, draft, actions.onToggle)
                }
            }
        }
        PresetCard(
            title = "Customize",
            subtitle = "Puzzles, games, deadline, friends' puzzles",
            selected = draft.preset == DuelPreset.Custom,
            onClick = {
                // Daily's rows hide the Customize body, so tapping Customize then takes the preset and opens it rather than closing it.
                if (!customizing || draft.showsRows(state.lineup, state.games)) {
                    actions.onCustomize()
                    customizing = true
                } else {
                    customizing = !customizing
                }
            },
            tag = "duel.preset.custom",
            trailing = { Chevron(expanded = customizing) },
        ) { CustomizeBody(customizing && !draft.showsRows(state.lineup, state.games)) { DuelCustomizePanel(state, actions, Modifier.padding(top = Spacing.sm)) } }
        state.notice?.let { Notice(it) }
        Text(
            text = state.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("duel.summary"),
        )
        PrimaryButton(
            text = "Create duel",
            onClick = actions.onCreate,
            enabled = state.canCreate,
            loading = state.creating,
            modifier = Modifier.fillMaxWidth().testTag("duel.create"),
        )
    }
}

/** One chip per game for today's puzzle: its number, or "Solved today" and unavailable once the player has it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyGames(games: List<DuelGame>, draft: DuelDraft, onToggle: (DuelGame) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        games.forEach { game ->
            val label = when {
                game.solvedToday -> "${game.name} · Solved today"
                game.todayNumber != null -> "${game.name} #${game.todayNumber}"
                else -> game.name
            }
            DuelGameChip(game, selected = game.id in draft.games, enabled = !game.solvedToday, label = label, onClick = { onToggle(game) })
        }
    }
}
