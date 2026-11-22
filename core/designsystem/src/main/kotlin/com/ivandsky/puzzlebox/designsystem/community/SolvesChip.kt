package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The "▶ 132" count on the art of a shelf card: white ink on a scrim, read out as "132 solves". */
@Composable
internal fun SolvesChip(solves: Int, modifier: Modifier = Modifier) {
    val count = ShelfCardDefaults.solvesText(solves)
    Row(
        modifier = modifier
            .background(ShelfCardDefaults.Scrim, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
            .semantics { contentDescription = "$count solves" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PuzzleIcons.Play, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
        Text(
            text = count,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
        )
    }
}
