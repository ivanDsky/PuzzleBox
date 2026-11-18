package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * How complete the board is ("2 of 5 games"), set under it so a partial result is never mistaken for a final one; the
 * line saying when its LinkedIn part was read ([tag] tells them apart) is set the same way. Plain text: it is not a button.
 */
@Composable
internal fun RanksBoardNote(text: String, modifier: Modifier = Modifier, tag: String = "ranks.board.note") {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .testTag(tag)
            .padding(vertical = Spacing.xs),
    )
}
