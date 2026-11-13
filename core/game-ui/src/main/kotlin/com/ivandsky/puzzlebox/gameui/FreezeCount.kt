package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalIce
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular

// Beside a labelLarge number the default 24dp glyph towers over the digits.
private val FreezeGlyphSize = 16.dp

/**
 * The streak freezes the player [held], as a snowflake and the count on an ice pill: the
 * one freeze-count element Home, Play and Result share, so the three never drift. Reads
 * [freezeCountLabel] aloud, since the pill itself shows only the number.
 */
@Composable
fun FreezeCount(held: Int, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    val label = freezeCountLabel(held)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
        shape = PillShape,
        color = ice.container,
        contentColor = ice.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PuzzleIcons.Snowflake, contentDescription = null, modifier = Modifier.size(FreezeGlyphSize))
            Text(text = held.toString(), style = MaterialTheme.typography.labelLarge.tabular())
        }
    }
}

/** "1 freeze" / "N freezes": the one wording of the held count for every streak surface. */
internal fun freezeCountLabel(held: Int): String = if (held == 1) "1 freeze" else "$held freezes"

@PuzzleBoxPreviews
@Composable
internal fun FreezeCountNonePreview() {
    PuzzleBoxTheme { FreezeCount(held = 0, modifier = Modifier.padding(Spacing.md)) }
}

@PuzzleBoxPreviews
@Composable
internal fun FreezeCountOnePreview() {
    PuzzleBoxTheme { FreezeCount(held = 1, modifier = Modifier.padding(Spacing.md)) }
}

@PuzzleBoxPreviews
@Composable
internal fun FreezeCountManyPreview() {
    PuzzleBoxTheme { FreezeCount(held = 5, modifier = Modifier.padding(Spacing.md)) }
}
