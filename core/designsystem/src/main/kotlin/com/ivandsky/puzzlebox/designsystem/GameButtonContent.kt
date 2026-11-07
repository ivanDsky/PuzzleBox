package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GameButtonIconSize: Dp = 24.dp
private val GameButtonIconGap: Dp = 2.dp

/** A game button's own face content: the icon over [label] (none on an icon-only tile), in [ink]. */
@Composable
fun GameButtonContent(icon: ImageVector, label: String?, ink: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameButtonIconGap),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(GameButtonIconSize), tint = ink)
        if (label != null) {
            Text(text = label, style = GameButtonTextStyle, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Previews — light + dark in PuzzleBoxTheme: labelled and icon-only, off any face.
@PuzzleBoxPreviews
@Composable
internal fun GameButtonContentPreview() {
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            GameButtonContent(icon = PuzzleIcons.Hint, label = "Hint", ink = MaterialTheme.colorScheme.onSurface)
            GameButtonContent(icon = PuzzleIcons.Erase, label = null, ink = MaterialTheme.colorScheme.onSurface)
        }
    }
}
