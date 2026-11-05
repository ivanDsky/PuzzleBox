package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing

private const val TABULAR_FIGURES = "tnum"

// M3's disabled-content emphasis; the key keeps working, it only reads as done.
private const val EXHAUSTED_CONTENT_ALPHA = 0.38f

/**
 * One digit key: the digit over its remaining count. A fully placed digit greys out
 * but stays clickable (A10), so a player can still overwrite a wrong cell with it.
 */
@Composable
internal fun SudokuDigitKey(
    digit: Int,
    remaining: Int,
    notesMode: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val exhausted = remaining == 0
    val targetContainer = when {
        exhausted -> scheme.surfaceContainerHighest
        notesMode -> scheme.tertiaryContainer
        else -> scheme.secondaryContainer
    }
    val targetContent = when {
        exhausted -> scheme.onSurface.copy(alpha = EXHAUSTED_CONTENT_ALPHA)
        notesMode -> scheme.onTertiaryContainer
        else -> scheme.onSecondaryContainer
    }
    val crossfade: AnimationSpec<Color> = if (reducedMotion) snap() else tween(MotionTokens.DurationShort)
    val container by animateColorAsState(targetContainer, crossfade, label = "digitKeyContainer")
    val content by animateColorAsState(targetContent, crossfade, label = "digitKeyContent")
    val label = digitKeyLabel(digit, remaining, notesMode)

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Spacing.xs)
                .clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = content,
            )
            Text(
                text = remaining.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = content,
            )
        }
    }
}

private fun digitKeyLabel(digit: Int, remaining: Int, notesMode: Boolean): String =
    if (notesMode) "Note $digit, $remaining left" else "$digit, $remaining left"
