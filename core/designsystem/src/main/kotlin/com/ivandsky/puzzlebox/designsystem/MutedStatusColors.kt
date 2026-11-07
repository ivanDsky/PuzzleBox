package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/**
 * The muted-status chip role, for a state that is over rather than live (a puzzle taken
 * down, a share withdrawn): a quiet slate [container] with [content] on top, its own pair
 * so it no longer borrows the neutral `surfaceContainerHighest`/`onSurfaceVariant` ramp.
 * Read it through [LocalMutedStatus]; both modes' pairs hold AA (MutedStatusTokenTest).
 */
data class MutedStatusColors(
    val container: Color,
    val content: Color,
)

// Catalog swatch: the role on a pill in light and dark, the way a status chip wears it.
@ShowkaseComposable(name = "MutedStatus", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun MutedStatusPreview() {
    PuzzleBoxTheme {
        val muted = LocalMutedStatus.current
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Surface(shape = PillShape, color = muted.container, contentColor = muted.content) {
                Text(
                    text = "Taken down",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }
}
