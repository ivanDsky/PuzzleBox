package com.ivandsky.puzzlebox.play.random

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameFilterChip
import com.ivandsky.puzzlebox.play.community.DifficultyChoice

/** "Difficulty" over single-select chips: Any, then each [DifficultyChoice]. [difficulty] null is Any; [enabled] false greys them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RandomDifficultyChips(
    difficulty: DifficultyChoice?,
    enabled: Boolean,
    onDifficulty: (DifficultyChoice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Difficulty", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GameFilterChip(
                brand = GameBrand.Neutral,
                label = "Any",
                selected = difficulty == null,
                onClick = { onDifficulty(null) },
                modifier = Modifier.testTag("random.difficulty.Any"),
                enabled = enabled,
            )
            DifficultyChoice.entries.forEach {
                GameFilterChip(
                    brand = GameBrand.Neutral,
                    label = it.label,
                    selected = difficulty == it,
                    onClick = { onDifficulty(it) },
                    modifier = Modifier.testTag("random.difficulty.${it.label}"),
                    enabled = enabled,
                )
            }
        }
    }
}
