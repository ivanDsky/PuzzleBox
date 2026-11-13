package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/**
 * The small hint mark of a replay board: a lightbulb on a secondary disc, the colour of the Hint bars, sized for the top-right corner of a
 * cell. Decorative, so it has no content description; the board owns the semantics.
 */
@Composable
fun ReplayHintBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp).background(MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
        Icon(PuzzleIcons.Hint, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSecondary)
    }
}
