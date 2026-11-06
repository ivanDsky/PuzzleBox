package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.LocalOnFill
import com.ivandsky.puzzlebox.designsystem.colors

private const val SECONDARY_ALPHA = 0.82f
private const val MOTIF_ALPHA = 0.18f

// The offline card is neutral grey, so its motif is fainter to stay behind the text.
private const val OFFLINE_MOTIF_ALPHA = 0.10f

/**
 * One hub card's paint (hub card handoff §Tokens): [fill] behind everything, [fg] for the
 * name, status and marks, [fg2] for the secondary lines, [motif] tinted at [motifAlpha].
 */
@Immutable
data class GameCardColors(
    val fill: Color,
    val fg: Color,
    val fg2: Color,
    val motif: Color,
    val motifAlpha: Float,
)

/**
 * A card in [brand]'s accent with on-fill content, or, when [offline], the neutral
 * surfaceContainerHigh card with every content role in onSurfaceVariant.
 */
@Composable
internal fun gameCardColors(brand: GameBrand, offline: Boolean): GameCardColors {
    if (offline) {
        val content = MaterialTheme.colorScheme.onSurfaceVariant
        return GameCardColors(
            fill = MaterialTheme.colorScheme.surfaceContainerHigh,
            fg = content,
            fg2 = content,
            motif = content,
            motifAlpha = OFFLINE_MOTIF_ALPHA,
        )
    }
    val onFill = LocalOnFill.current
    return GameCardColors(
        fill = brand.accent.colors().accent,
        fg = onFill,
        fg2 = onFill.copy(alpha = SECONDARY_ALPHA),
        motif = onFill,
        motifAlpha = MOTIF_ALPHA,
    )
}
