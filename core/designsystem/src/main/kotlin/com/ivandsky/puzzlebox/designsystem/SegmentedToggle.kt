package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow

/**
 * The either/or switch: one segment per [options] entry, [selected] drawn chosen, a tap
 * reporting its option through [onSelect]. Each segment shows [label] on one line and carries
 * the test tag [tag]; [enabled] dims and blocks all of them.
 */
@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                modifier = Modifier.testTag(tag(option)),
            ) { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun SegmentedTogglePreview() {
    PuzzleBoxTheme {
        SegmentedToggle(
            options = listOf("System", "Light", "Dark"),
            selected = "Light",
            onSelect = {},
            label = { it },
            tag = { it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
