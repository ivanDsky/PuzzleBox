package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The share code of the puzzle a two-pane list is showing on its other side (the Community tab on the tablet), or null
 * where no list row is the picked one. A row of that code draws itself with [selectedRow].
 */
val LocalSelectedRowCode = staticCompositionLocalOf<String?> { null }

/**
 * Outlines a list row that is the picked one in a two-pane (a 2 dp outline in the text colour, in the large corner
 * shape) and marks it selected for accessibility; an unpicked row only carries `selected = false`. Put it where the
 * outline should sit, after any clip and before the row's own padding.
 */
@Composable
fun Modifier.selectedRow(selected: Boolean): Modifier =
    semantics { this.selected = selected }.then(
        if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, AppShapes.large) else Modifier,
    )

@PuzzleBoxPreviews
@Composable
private fun SelectedRowPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg)) {
            listOf(true, false).forEach { picked ->
                Box(Modifier.fillMaxWidth().height(72.dp).selectedRow(picked).padding(Spacing.lg)) {
                    Text(if (picked) "Picked" else "Not picked", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
