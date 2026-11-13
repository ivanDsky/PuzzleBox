package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/**
 * The small hint mark of a replay board: a lightbulb on a secondary disc, the colour of the Hint bars.
 * It sizes itself from the cell it sits in: the disc is [DISC_FRACTION] of the cell width, the icon
 * [ICON_FRACTION] of the disc. Place it with `Modifier.align(Alignment.TopEnd)` inside a Box the size of
 * the cell. Decorative, so it has no content description; the board owns the semantics.
 */
@Composable
fun ReplayHintBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth(DISC_FRACTION).aspectRatio(1f).background(MaterialTheme.colorScheme.secondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PuzzleIcons.Hint, contentDescription = null, modifier = Modifier.fillMaxSize(ICON_FRACTION), tint = MaterialTheme.colorScheme.onSecondary)
    }
}

private const val DISC_FRACTION = 0.18f
private const val ICON_FRACTION = 0.7f
