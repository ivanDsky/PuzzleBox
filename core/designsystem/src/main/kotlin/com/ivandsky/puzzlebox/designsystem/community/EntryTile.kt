package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * A link tile to a community page (From friends, Your puzzles): a [leading] picture, the [title] and a
 * quiet [subtitle] such as "7 puzzles". It is a link, not a puzzle card, so it has no art. 64dp at the
 * least; in the light theme a 1dp outline keeps it off the page.
 */
@Composable
fun EntryTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ShelfCardDefaults.CardCorner)
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .cardPressScale(interaction, ShelfCardDefaults.PressScale, reduced)
            .heightIn(min = ShelfCardDefaults.EntryTileMinHeight)
            .clip(shape)
            .background(scheme.surfaceContainerLow)
            .then(if (LocalDarkTheme.current) Modifier else Modifier.border(1.dp, scheme.outlineVariant, shape))
            .cardPressWash(interaction, reduced, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = subtitle,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
            )
        }
    }
}
