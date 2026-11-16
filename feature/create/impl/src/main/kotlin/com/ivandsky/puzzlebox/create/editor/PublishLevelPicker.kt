package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level

/**
 * The level a Public publish is listed at (96): Easy, Medium, Hard and Expert as flat chips, each at least 48dp tall and
 * wide, the [choice]'s chosen one marked, with "Proposed: Hard" under them so a changed level shows what the Studio
 * proposed. A tap reports the level ([onPick]); the chips turn off while not [enabled].
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PublishLevelPicker(
    choice: LevelChoice,
    onPick: (Level) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.testTag("create.details.level"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            for (level in Level.entries) {
                FilterChip(
                    selected = choice.chosen == level,
                    onClick = { onPick(level) },
                    enabled = enabled,
                    label = { Text(level.name) },
                    modifier = Modifier.heightIn(min = TargetSize).widthIn(min = TargetSize).testTag("create.details.level.${level.name.lowercase()}"),
                )
            }
        }
        Text(
            text = "Proposed: ${choice.proposed.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("create.details.level.proposed"),
        )
    }
}

private val TargetSize = 48.dp

@PuzzleBoxPreviews
@Composable
private fun PublishLevelPickerPreview() {
    PuzzleBoxTheme { PublishLevelPicker(LevelChoice(proposed = Level.Hard, chosen = Level.Hard), onPick = {}) }
}

@PuzzleBoxPreviews
@Composable
private fun PublishLevelPickerChangedPreview() {
    PuzzleBoxTheme { PublishLevelPicker(LevelChoice(proposed = Level.Hard, chosen = Level.Easy), onPick = {}) }
}
