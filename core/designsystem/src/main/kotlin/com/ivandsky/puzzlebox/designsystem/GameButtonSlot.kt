package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * A [GameButton] whose face shows [content], given the face's ink, where the icon and
 * label would sit: a button that is also a readout, like the Strands hint meter. [label]
 * is its accessible name; [content] is laid out over the whole face.
 */
@Composable
fun GameButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: AccentSlot? = null,
    size: GameButtonSize = GameButtonSize.Regular,
    content: @Composable BoxScope.(ink: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    GameButtonFace(
        colors = gameButtonColors(accent?.colors(), MaterialTheme.colorScheme, LocalNeutralLip.current),
        enabled = enabled,
        interactionSource = interactionSource,
        label = label,
        describe = true,
        size = size,
        modifier = modifier.gameButtonClick(interactionSource, enabled, onClick),
        content = content,
    )
}

// Previews — light + dark in PuzzleBoxTheme: a slotted face, neutral and accent.
@PuzzleBoxPreviews
@Composable
internal fun GameButtonSlotPreview() {
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            GameButton(onClick = {}, label = "Hint", size = GameButtonSize.Tall) { ink ->
                GameButtonContent(icon = PuzzleIcons.Hint, label = "Hint", ink = ink)
            }
            GameButton(onClick = {}, label = "Hint", accent = AccentSlot.Pink, size = GameButtonSize.Tall) { ink ->
                GameButtonContent(icon = PuzzleIcons.Hint, label = "Hint", ink = ink)
            }
        }
    }
}
