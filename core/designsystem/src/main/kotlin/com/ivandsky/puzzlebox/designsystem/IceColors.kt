package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/**
 * The ice accent role, for a day a streak freeze covers: a pale cool-blue [container] with
 * [onContainer] on top, a hue of its own beside the green primary, the amber warning and
 * the slate muted status. Read it through [LocalIce]; both modes' pairs hold AA
 * (IceTokenTest).
 */
data class IceColors(
    val container: Color,
    val onContainer: Color,
)

// Catalog swatch: the role on a pill with the snowflake, in light and dark.
@ShowkaseComposable(name = "Ice", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun IcePreview() {
    PuzzleBoxTheme {
        val ice = LocalIce.current
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Surface(shape = PillShape, color = ice.container, contentColor = ice.onContainer) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(imageVector = PuzzleIcons.Snowflake, contentDescription = null)
                    Text(text = "Frozen", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
