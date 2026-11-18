package com.ivandsky.puzzlebox.compete.create

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.lineup.LineupAudience
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.compete.create.lineup.LineupPanel
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

private val INFO_MARK = 20.dp
private val INFO_STROKE = 1.5.dp

/**
 * The panel for a duel against Past you: Quick or Customize (no Daily, no deadline), the footer or, when the chosen
 * games have no solved puzzle, the reason Create is off, and Create duel.
 */
@Composable
internal fun SelfCreatePanel(state: NewDuelState, actions: NewDuelActions) {
    var customizing by rememberSaveable { mutableStateOf(false) }
    val self = state.self
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PresetCard("Quick duel", "Best of 3 · mixed solved puzzles", self.form.preset == DuelPreset.Quick, actions.onQuick, "duel.preset.quick")
        PresetCard(
            title = "Customize",
            subtitle = "Puzzles, games, friends' puzzles",
            selected = self.form.preset == DuelPreset.Custom,
            onClick = {
                // With the lineup on, Customize is a preset of its own: a tap that finds the panel open on Quick takes the preset, not closes the panel.
                if (state.lineup != null && (!customizing || self.form.preset != DuelPreset.Custom)) {
                    actions.onCustomize()
                    customizing = true
                } else {
                    customizing = !customizing
                }
            },
            tag = "duel.preset.custom",
            trailing = { Chevron(expanded = customizing) },
        ) {
            CustomizeBody(customizing) {
                val lineup = state.lineup
                if (lineup != null) {
                    SelfLineupPanel(lineup, state, actions, Modifier.padding(top = Spacing.sm))
                } else {
                    SelfCustomizePanel(self, state.games, actions, Modifier.padding(top = Spacing.sm))
                }
            }
        }
        state.notice?.let { Notice(it) }
        if (self.empty) {
            NoSolvedReason(if (state.lineup != null) "You haven't solved any puzzles yet." else "You haven't solved any ${self.soleGame?.let { "$it puzzles" } ?: "puzzles in these games"} yet.")
        } else if (state.summary.isNotBlank()) {
            Text(
                text = state.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("duel.summary"),
            )
        }
        PrimaryButton(
            text = "Create duel",
            onClick = actions.onCreate,
            enabled = state.canCreate,
            loading = state.creating,
            modifier = Modifier.fillMaxWidth().testTag("duel.create"),
        )
    }
}

/** Rounds (held to the solved pool), one chip per game with how many puzzles you've solved of it, and the friends' puzzles switch. */
@Composable
private fun SelfCustomizePanel(self: SelfDuelView, games: List<DuelGame>, actions: NewDuelActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("duel.customize"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Section("PUZZLES · BEST OF") {
            RoundsRow(self.rounds, actions.onCount, max = self.cap)
            if (self.capped) {
                val what = self.soleGame?.let { "$it puzzles" } ?: "puzzles in these games"
                Text(
                    text = "You've solved ${self.cap} $what, so the duel stops at ${self.cap}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("duel.self.cap"),
                )
            }
        }
        Section("GAMES") {
            SolvedGameChips(self, games, actions.onToggle)
            HintsFirstNote(games, self.form.games)
        }
        FriendsRow(self.form.includeFriends, actions.onFriends, hint = "Adds friend puzzles you've solved")
    }
}

/** With the lineup on, Customize is the ordered lineup of "Any" rows over what the player has solved, and the friends' puzzles switch; no stepper, no game chips. */
@Composable
private fun SelfLineupPanel(lineup: LineupEditor, state: NewDuelState, actions: NewDuelActions, modifier: Modifier = Modifier) {
    val self = state.self
    Column(modifier.fillMaxWidth().testTag("duel.customize"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        LineupPanel(lineup, state.games, self.form.includeFriends, actions.onEditLineup, audience = LineupAudience.PastYou(self.counts?.byGame.orEmpty()))
        FriendsRow(self.form.includeFriends, actions.onFriends, hint = "Adds friend puzzles you've solved")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SolvedGameChips(self: SelfDuelView, games: List<DuelGame>, onToggle: (DuelGame) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        games.forEach { game ->
            DuelGameChip(
                game = game,
                selected = game.id in self.form.games,
                enabled = true,
                label = "${game.name} · ${self.solved(game)} solved",
                onClick = { onToggle(game) },
            )
        }
    }
}

/** The one-line reason Create is off: [text], the chosen games having no solved puzzle. */
@Composable
private fun NoSolvedReason(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(INFO_MARK).border(INFO_STROKE, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("i", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("duel.self.reason"),
        )
    }
}
