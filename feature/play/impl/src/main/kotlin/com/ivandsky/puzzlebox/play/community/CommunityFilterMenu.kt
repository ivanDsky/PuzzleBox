package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.DifficultyIcon

private val TouchTarget = 48.dp

/**
 * The community list's filter: a 48dp ⋮ that opens a menu with two groups, Game (All, then each of [games]) and
 * Difficulty (Any, then each [DifficultyChoice]). The current [game] and [difficulty] carry a check. Picking an item
 * closes the menu; picking the difficulty already checked drops it (the view model's rule).
 */
@Composable
internal fun CommunityFilterMenu(
    games: List<GameChoice>,
    game: GameId?,
    difficulty: DifficultyChoice?,
    onGame: (GameId?) -> Unit,
    onDifficulty: (DifficultyChoice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(TouchTarget).testTag("community.filter.menu")) {
            Icon(PuzzleIcons.More, contentDescription = "Filter puzzles", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GroupLabel("Game")
            FilterItem("All", checked = game == null, tag = "community.filter.game.all") { pick(onGame, null) { open = false } }
            games.forEach { choice ->
                FilterItem(choice.name, checked = game == choice.id, tag = "community.filter.game.${choice.id.value}") {
                    pick(onGame, choice.id) { open = false }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
            GroupLabel("Difficulty")
            FilterItem("Any", checked = difficulty == null, tag = "community.filter.difficulty.any") {
                pick(onDifficulty, null) { open = false }
            }
            DifficultyChoice.entries.forEach { choice ->
                FilterItem(
                    choice.label,
                    checked = difficulty == choice,
                    tag = "community.filter.difficulty.${choice.name.lowercase()}",
                    mark = choice.mark,
                ) { pick(onDifficulty, choice) { open = false } }
            }
        }
    }
}

private inline fun <T> pick(report: (T) -> Unit, value: T, close: () -> Unit) {
    close()
    report(value)
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

/** One menu row: the level [mark] when it has one, [label], a check while [checked], and the [checked] state for accessibility. */
@Composable
private fun FilterItem(label: String, checked: Boolean, tag: String, mark: Difficulty.Level? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = mark?.let { { DifficultyIcon(it) } },
        modifier = Modifier.testTag(tag).semantics { selected = checked },
        trailingIcon = if (checked) ({ Icon(PuzzleIcons.Check, contentDescription = "Selected") }) else null,
    )
}
