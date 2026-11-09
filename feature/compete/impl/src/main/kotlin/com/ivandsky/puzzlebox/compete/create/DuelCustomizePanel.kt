package com.ivandsky.puzzlebox.compete.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import org.koin.compose.koinInject

private val QUICK_COUNTS = listOf(3, 5, 7)

/**
 * The Customize panel: rounds (a stepper and 3/5/7 chips; a today-only duel plays each chosen game once, so it has
 * none), one chip per game, the deadline, and the friends' puzzles switch. Every edit goes through [actions].
 */
@Composable
internal fun DuelCustomizePanel(state: NewDuelState, actions: NewDuelActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    Column(modifier.fillMaxWidth().testTag("duel.customize"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (!draft.todayOnly) {
            Section("ROUNDS · BEST OF") { RoundsRow(draft.count, actions.onCount) }
        }
        Section("GAMES") {
            GameChips(state.games, draft, actions.onToggle)
            state.games.firstOrNull { it.id in draft.games && it.hintsFirst }?.let {
                Text(
                    text = "${it.name} ranks hints first, then time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("duel.customize.hintsFirst"),
                )
            }
        }
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

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun RoundsRow(count: Int, onCount: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onCount(count - 1) },
            enabled = count > DuelDraft.MIN_COUNT,
            modifier = Modifier.semantics { contentDescription = "Fewer rounds" }.testTag("duel.rounds.fewer"),
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("duel.rounds.count"),
        )
        IconButton(
            onClick = { onCount(count + 1) },
            enabled = count < Match.MAX_LEGS,
            modifier = Modifier.semantics { contentDescription = "More rounds" }.testTag("duel.rounds.more"),
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        QUICK_COUNTS.forEach { quick ->
            FilterChip(
                selected = count == quick,
                onClick = { onCount(quick) },
                label = { Text(quick.toString()) },
                modifier = Modifier.testTag("duel.rounds.$quick"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameChips(games: List<DuelGame>, draft: DuelDraft, onToggle: (DuelGame) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        games.forEach { game ->
            val solved = draft.todayOnly && game.solvedToday
            DuelGameChip(
                game = game,
                selected = game.id in draft.games,
                enabled = !solved,
                label = if (solved) "${game.name} · Solved today" else game.name,
                onClick = { onToggle(game) },
            )
        }
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

@Composable
private fun FriendsRow(included: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text("Friends' puzzles", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "Adds puzzles friends sent you",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = included, onCheckedChange = onChange, modifier = Modifier.testTag("duel.friends"))
    }
}
