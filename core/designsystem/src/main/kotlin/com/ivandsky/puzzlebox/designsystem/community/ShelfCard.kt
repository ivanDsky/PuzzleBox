package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * A puzzle on a community shelf: 156dp wide, the game's art on top (its [glyph] disc in [accent], the
 * solves chip bottom-left, any [badges] top-right, NEW first), then the [title] over two lines and the
 * "[author] · [difficulty]" line. A tap [onClick]s; a long press [onLongClick]s (report, remove a
 * friend) with [onLongClickLabel] announced for it. [selected] rings the card that is open in the tablet's
 * detail pane. A press scales it to .97 and, under reduced motion, washes it instead.
 */
@Composable
fun ShelfCard(
    title: String,
    author: String,
    solves: Int,
    accent: AccentSlot,
    glyph: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    difficulty: String? = null,
    badges: List<ShelfBadge> = emptyList(),
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ShelfCardDefaults.CardCorner)
    Column(
        modifier = modifier
            .cardPressScale(interaction, ShelfCardDefaults.PressScale, reduced)
            .width(ShelfCardDefaults.CardWidth)
            .semantics { this.selected = selected }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        GameArt(
            accent = accent,
            glyph = glyph,
            discSize = ShelfCardDefaults.DiscSize,
            modifier = Modifier
                .width(ShelfCardDefaults.CardWidth)
                .height(ShelfCardDefaults.ArtHeight)
                .clip(shape)
                .cardPressWash(interaction, reduced, shape),
        ) {
            SolvesChip(solves, Modifier.align(Alignment.BottomStart).padding(Spacing.sm))
            if (badges.isNotEmpty()) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) { badges.forEach { ShelfBadgeLabel(it) } }
            }
            if (selected) Box(Modifier.matchParentSize().border(ShelfCardDefaults.SelectionRing, MaterialTheme.colorScheme.secondary, shape))
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = Spacing.sm),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = ShelfCardDefaults.byline(author, difficulty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        )
    }
}
