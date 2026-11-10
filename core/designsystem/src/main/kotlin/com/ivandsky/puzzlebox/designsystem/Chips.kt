package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Status read-outs during play. Pills on container colors so they read at a glance;
// both container/on pairs are guarded by the WCAG AA test in ColorTokensTest.
@Composable
fun ScoreChip(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    tone: DuelTone = DuelTone.Me,
) {
    val scheme = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (tone) {
        DuelTone.Me -> scheme.primaryContainer to scheme.onPrimaryContainer
        DuelTone.Them -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    ChipSurface(modifier, containerColor = containerColor) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
        )
    }
}

@Composable
fun HintChip(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    ChipSurface(modifier, containerColor = scheme.tertiaryContainer) {
        Text(
            text = if (count == 1) "1 hint" else "$count hints",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun ChipSurface(
    modifier: Modifier,
    containerColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// Previews — Studio tooling only, zero public API surface (internal, not private, so
// Showkase's generated index can access them). Exactly
// light + dark, always inside PuzzleBoxTheme. Neither chip animates —
// no infinite transition, no trigger — so static panes show the resting state.

@ShowkaseComposable(name = "ScoreChip", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun ScoreChipPreview() {
    PuzzleBoxTheme {
        ScoreChip(value = "1:24", label = "Score")
    }
}

@ShowkaseComposable(name = "ScoreChip (Them)", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun ScoreChipThemPreview() {
    PuzzleBoxTheme {
        ScoreChip(value = "0:58", label = "Score", tone = DuelTone.Them)
    }
}

@ShowkaseComposable(name = "HintChip", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun HintChipPreview() {
    PuzzleBoxTheme {
        HintChip(count = 3)
    }
}
