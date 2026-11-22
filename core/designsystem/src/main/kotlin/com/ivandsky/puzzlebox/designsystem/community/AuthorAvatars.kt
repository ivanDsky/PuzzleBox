package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.colors

/**
 * Up to three author initials as overlapping 28dp discs in game colours, the lead of the From friends
 * tile. Decorative: the tile's own text says how many puzzles there are.
 */
@Composable
fun AuthorAvatars(initials: List<Char>, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Row(modifier = modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(-OVERLAP)) {
        initials.take(MAX_SHOWN).forEachIndexed { index, initial ->
            val colors = AVATAR_ACCENTS[index % AVATAR_ACCENTS.size].colors()
            Box(
                modifier = Modifier
                    .size(AVATAR)
                    .background(colors.accent, CircleShape)
                    .border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial.uppercase(),
                    color = colors.onAccent,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold),
                )
            }
        }
    }
}

private val AVATAR = 28.dp
private val OVERLAP = 8.dp
private const val MAX_SHOWN = 3
private val AVATAR_ACCENTS = listOf(AccentSlot.Blue, AccentSlot.Violet, AccentSlot.Pink, AccentSlot.Orange)
