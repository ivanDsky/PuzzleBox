package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * The #1 most played puzzle as a hero card: a 112dp art square on the left, then the [kicker], the
 * [title] over two lines, "[gameName] · [difficulty] · by [author]" and the solve count. Same press,
 * long press and [selected] ring as [ShelfCard], with a gentler .985 press scale.
 */
@Composable
fun SpotlightCard(
    title: String,
    gameName: String,
    author: String,
    solves: Int,
    accent: AccentSlot,
    glyph: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    difficulty: String? = null,
    kicker: String = ShelfCardDefaults.SpotlightKicker,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ShelfCardDefaults.SpotlightCorner)
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .cardPressScale(interaction, ShelfCardDefaults.SpotlightPressScale, reduced)
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .cardPressWash(interaction, reduced, shape)
            .then(if (selected) Modifier.border(ShelfCardDefaults.SelectionRing, scheme.secondary, shape) else Modifier)
            .semantics { this.selected = selected }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameArt(
            accent = accent,
            glyph = glyph,
            discSize = ShelfCardDefaults.SpotlightDiscSize,
            modifier = Modifier
                .size(ShelfCardDefaults.SpotlightArtSize)
                .clip(RoundedCornerShape(ShelfCardDefaults.SpotlightArtCorner)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = kicker,
                color = LocalPrimaryText.current,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp),
            )
            Text(
                text = title,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = ShelfCardDefaults.spotlightLine(gameName, difficulty, author),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            )
            val count = ShelfCardDefaults.solvesText(solves)
            Row(
                // The play triangle starts inside its box; pull the row left by that inset so the glyph, not the box, is flush with the text above.
                modifier = Modifier.offset(x = -ShelfCardDefaults.PlayGlyphInset).semantics { contentDescription = "$count solves" },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PuzzleIcons.Play, contentDescription = null, modifier = Modifier.size(ShelfCardDefaults.PlayGlyphSize), tint = LocalPrimaryText.current)
                Text(
                    text = "$count solves",
                    color = LocalPrimaryText.current,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
