package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.Hidden
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The words on the veil's pill. */
internal const val HIDDEN_BOARD_PILL = "Hidden until you play"

/**
 * What lies over the blurred board: the surface colour at [HiddenBoardTokens.VeilAlpha], and in its middle a flat
 * pill with a struck-through eye that says why the board cannot be read. [pillAlpha] is read only when the
 * pill is drawn, so the reveal fades it without recomposing.
 */
@Composable
internal fun HiddenBoardVeil(pillAlpha: () -> Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.copy(alpha = HiddenBoardTokens.VeilAlpha))
            .padding(Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer { alpha = pillAlpha() }
                .defaultMinSize(minHeight = HiddenBoardTokens.PillMinHeight)
                .clip(PillShape)
                .background(colors.surface)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(HiddenBoardTokens.PillGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PuzzleIcons.Hidden,
                contentDescription = null,
                modifier = Modifier.size(HiddenBoardTokens.PillIcon),
                tint = colors.onSurface,
            )
            Text(
                text = HIDDEN_BOARD_PILL,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
