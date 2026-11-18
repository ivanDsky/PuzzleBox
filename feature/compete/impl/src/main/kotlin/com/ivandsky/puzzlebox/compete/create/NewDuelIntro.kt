package com.ivandsky.puzzlebox.compete.create

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The one line under New duel's title: what the duel is against a friend, or against Past you. */
@Composable
internal fun NewDuelIntro(opponent: Opponent, modifier: Modifier = Modifier) {
    Text(
        text = if (opponent == Opponent.PastYou) {
            "Race your first solve of each puzzle. It stays on this device."
        } else {
            "Same puzzles for you both, played in your own time. Opponent times stay sealed until you finish."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
