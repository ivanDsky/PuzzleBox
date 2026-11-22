package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * The dashed "Make a [gameName] puzzle" card that ends a game's shelf: a 36dp icon tile over the words,
 * 120dp tall. It is [ShelfCardDefaults.MakeCardWidth] beside puzzles; pass
 * `Modifier.width(ShelfCardDefaults.MakeRowCardWidth)` in the make row or on an empty shelf.
 */
@Composable
fun MakePuzzleCard(
    gameName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ShelfCardDefaults.CardCorner)
    val scheme = MaterialTheme.colorScheme
    val dash = scheme.outline
    Column(
        modifier = modifier
            .cardPressScale(interaction, ShelfCardDefaults.PressScale, reduced)
            .width(ShelfCardDefaults.MakeCardWidth)
            .heightIn(min = ShelfCardDefaults.ArtHeight)
            .clip(shape)
            .cardPressWash(interaction, reduced, shape)
            .drawBehind {
                val stroke = ShelfCardDefaults.MakeDashWidth.toPx()
                val half = stroke / 2
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(half, half),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(ShelfCardDefaults.CardCorner.toPx() - half),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), GAP.toPx()))),
                )
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            PuzzleIcons.Add,
            contentDescription = null,
            modifier = Modifier
                .size(ShelfCardDefaults.MakeIconTile)
                .background(scheme.primaryContainer, MaterialTheme.shapes.small)
                .padding(Spacing.sm),
            tint = scheme.onPrimaryContainer,
        )
        Text(
            text = ShelfCardDefaults.makeLabel(gameName),
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
        )
    }
}

private val DASH = 6.dp
private val GAP = 4.dp
