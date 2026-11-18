package com.ivandsky.puzzlebox.compete.create

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.ivandsky.puzzlebox.compete.create.lineup.LineupPanel
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

private val INFO_MARK = 20.dp
private val INFO_STROKE = 1.5.dp

/** The panel for a duel against Past you: the choices, then the footer and Create duel. */
@Composable
internal fun SelfCreatePanel(state: NewDuelState, actions: NewDuelActions) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SelfChoices(state, actions)
        SelfCreateFooter(state, actions)
    }
}

/** Quick or Customize (no Daily, no deadline). [sheetPicker] false: the lineup shows no add sheet, the screen shows the picker. */
@Composable
internal fun SelfChoices(state: NewDuelState, actions: NewDuelActions, sheetPicker: Boolean = true) {
    var customizing by rememberSaveable { mutableStateOf(false) }
    val self = state.self
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PresetCard("Quick duel", "Best of 3 · mixed solved puzzles", self.form.preset == DuelPreset.Quick, actions.onQuick, "duel.preset.quick")
        PresetCard(
            title = "Customize",
            subtitle = "Puzzles, games, friends' puzzles",
            selected = self.form.preset == DuelPreset.Custom,
            onClick = {
                // Customize is a preset of its own: a tap that finds the panel open on Quick takes the preset, not closes the panel.
                if (!customizing || self.form.preset != DuelPreset.Custom) {
                    actions.onCustomize()
                    customizing = true
                } else {
                    customizing = !customizing
                }
            },
            tag = "duel.preset.custom",
            trailing = { Chevron(expanded = customizing) },
        ) {
            CustomizeBody(customizing) { SelfLineupPanel(state, actions, Modifier.padding(top = Spacing.sm), sheetPicker) }
        }
    }
}

/** The notice, the summary line or, when the chosen games have no solved puzzle, the reason Create is off, and Create duel. */
@Composable
internal fun SelfCreateFooter(state: NewDuelState, actions: NewDuelActions) {
    val self = state.self
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        state.notice?.let { Notice(it) }
        if (self.empty) {
            NoSolvedReason("You haven't solved any puzzles yet.")
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

/** Customize is the ordered lineup of "Any" rows over what the player has solved, and the friends' puzzles switch. */
@Composable
private fun SelfLineupPanel(state: NewDuelState, actions: NewDuelActions, modifier: Modifier = Modifier, sheetPicker: Boolean = true) {
    val self = state.self
    Column(modifier.fillMaxWidth().testTag("duel.customize"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        LineupPanel(state.lineup, state.games, self.form.includeFriends, actions.onEditLineup, audience = LineupAudience.PastYou(self.counts?.byGame), sheetPicker = sheetPicker)
        FriendsRow(self.form.includeFriends, actions.onFriends, hint = "Adds friend puzzles you've solved")
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
