package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The app's overflow and long-press menu: a raised card in the dialog's container colour with
 * a hairline edge, so it reads as a surface over the page even where the shadow is faint.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = AppShapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(MenuEdge, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

private val MenuEdge = 1.dp

// Previews — Studio tooling only. The menu draws in its own popup window below its anchor, so
// the preview reserves the room the two items drop into.

@ShowkaseComposable(name = "AppDropdownMenu", group = "Menus")
@PuzzleBoxPreviews
@Composable
internal fun AppDropdownMenuPreview() {
    PuzzleBoxTheme {
        Box(Modifier.padding(Spacing.lg).height(Spacing.xxl * 4)) {
            Box {
                AppDropdownMenu(expanded = true, onDismissRequest = {}) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {})
                    DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = {})
                }
            }
        }
    }
}
