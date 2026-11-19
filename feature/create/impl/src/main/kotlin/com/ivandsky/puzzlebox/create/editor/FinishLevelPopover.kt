package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.resultui.DifficultyIcon

/**
 * The Finish level picker (90): a [StripPopover] above the tool strip that asks how hard the
 * finished board should be, one chip per level with [current] (the author's last choice this
 * session, Medium at first) checked. A pick goes to [onPick], and picking the checked level again
 * asks for another board at it; an outside tap or Back goes to [onDismiss].
 */
@Composable
internal fun FinishLevelPopover(current: Difficulty.Level, onPick: (Difficulty.Level) -> Unit, onDismiss: () -> Unit) {
    StripPopover(onDismiss) { FinishLevelPanel(current, onPick) }
}

/** The popover's card: the question, the four levels, then what the harder two cost. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishLevelPanel(current: Difficulty.Level, onPick: (Difficulty.Level) -> Unit) {
    PopoverPanel("create.finish.levels") {
        Text("How hard should it be?", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Difficulty.Level.entries.forEach { level ->
                PopoverChip(level.name, level == current, "create.finish.level.${level.name}", { DifficultyIcon(level) }) { onPick(level) }
            }
        }
        Text(
            text = "Hard and Expert can take a few seconds longer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Preview: the panel on its first open (Medium checked) and after the author chose Expert.
@PuzzleBoxPreviews
@Composable
private fun FinishLevelPanelPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                FinishLevelPanel(current = Difficulty.Level.Medium, onPick = {})
                FinishLevelPanel(current = Difficulty.Level.Expert, onPick = {})
            }
        }
    }
}
