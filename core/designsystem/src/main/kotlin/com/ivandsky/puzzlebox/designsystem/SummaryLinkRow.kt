package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The one look of a summary link (ADR-0027 `summary.<type>`): a full-width rounded card in the
 * stats card's surface, at least 56dp tall, [text] at the start and a chevron at the end. A
 * [text] too long for one line wraps and the row grows to hold it. Callers tag it through [modifier].
 */
@Composable
fun SummaryLinkRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(PuzzleIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@ShowkaseComposable(name = "SummaryLinkRow", group = "Rows")
@PuzzleBoxPreviews
@Composable
internal fun SummaryLinkRowPreview() {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SummaryLinkRow(text = "Your history", onClick = {})
                SummaryLinkRow(text = "Today's rank: #12 on Mini Sudoku, the hardest board of the whole week", onClick = {})
            }
        }
    }
}
