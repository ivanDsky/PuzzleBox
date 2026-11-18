package com.ivandsky.puzzlebox.compete.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.compete.create.lineup.LineupPanel
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import org.koin.compose.koinInject

/** The Customize panel: the lineup's ordered puzzles, the deadline, and the friends' puzzles switch. Every edit goes through [actions]. */
@Composable
internal fun DuelCustomizePanel(state: NewDuelState, actions: NewDuelActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    Column(modifier.fillMaxWidth().testTag("duel.customize"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        LineupPanel(state.lineup, state.games, draft.includeFriends, actions.onEditLineup)
        Section("DEADLINE") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Match.DEADLINE_CHOICES.forEach { hours ->
                    FilterChip(
                        selected = draft.deadlineHours == hours,
                        onClick = { actions.onDeadline(hours) },
                        label = { Text("$hours h") },
                        modifier = Modifier.testTag("duel.deadline.$hours"),
                    )
                }
            }
        }
        FriendsRow(draft.includeFriends, actions.onFriends)
    }
}

/** One labelled group of the Customize panel. */
@Composable
internal fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** One game's chip: its glyph and [label]; a solved game in a today-only duel is [enabled] false. */
@Composable
internal fun DuelGameChip(game: DuelGame, selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val brand = koinInject<GamePlayRegistry>().brandOf(game.id)
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { GameGlyph(brand, GameGlyphSize.Inline) },
        modifier = Modifier.testTag("duel.game.${game.id.value}"),
    )
}

/** The friends' puzzles switch, with [hint] saying whose puzzles it adds. */
@Composable
internal fun FriendsRow(included: Boolean, onChange: (Boolean) -> Unit, hint: String = "Adds puzzles friends sent you") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text("Friends' puzzles", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = included, onCheckedChange = onChange, modifier = Modifier.testTag("duel.friends"))
    }
}
