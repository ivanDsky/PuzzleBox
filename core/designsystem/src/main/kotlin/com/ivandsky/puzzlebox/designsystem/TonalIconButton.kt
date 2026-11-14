package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/**
 * [SecondaryButton] as a bare glyph: a 48 dp round tonal icon button on the same fill and ink.
 * [contentDescription] names it for TalkBack and shows as a plain tooltip on long press.
 * [modifier] lands on the button itself (a test tag there reaches its semantics), not on the tooltip anchor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TonalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(ButtonMinHeight).pressScale(interactionSource, reduced = false),
            enabled = enabled,
            interactionSource = interactionSource,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

// Preview: the resting pair, light and dark; the press scale and the long-press tooltip are touch-driven.
@ShowkaseComposable(name = "TonalIconButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun TonalIconButtonPreview() {
    PuzzleBoxTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TonalIconButton(icon = PuzzleIcons.Share, contentDescription = "Share", onClick = {})
            TonalIconButton(icon = PuzzleIcons.Retry, contentDescription = "Play again", onClick = {})
        }
    }
}
