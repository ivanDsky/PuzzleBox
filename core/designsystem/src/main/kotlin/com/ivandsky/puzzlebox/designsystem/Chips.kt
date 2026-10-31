package com.ivandsky.puzzlebox.designsystem

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
) {
    val scheme = MaterialTheme.colorScheme
    ChipSurface(modifier, containerColor = scheme.primaryContainer) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimaryContainer,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onPrimaryContainer,
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
