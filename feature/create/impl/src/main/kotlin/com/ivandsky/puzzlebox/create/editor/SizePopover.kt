package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId

/** The most sizes the hint names one by one; a longer table reads as a range. */
private const val MaxListedSizes = 4

/**
 * The size popover (02 handoff "Size popover"): a [StripPopover] above the tool strip with one
 * chip per entry of [sizes], the game's variant table (A5), and [current] checked. A pick goes
 * to [onPick]; an outside tap or Back goes to [onDismiss].
 */
@Composable
internal fun SizePopover(
    sizes: List<VariantId>,
    current: VariantId,
    label: (VariantId) -> String,
    onPick: (VariantId) -> Unit,
    onDismiss: () -> Unit,
) {
    StripPopover(onDismiss) { SizePanel(sizes, current, label, onPick) }
}

/** The popover's card: the size chips, then the range hint. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizePanel(
    sizes: List<VariantId>,
    current: VariantId,
    label: (VariantId) -> String,
    onPick: (VariantId) -> Unit,
) {
    PopoverPanel("create.size.popover") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            sizes.forEach { size ->
                PopoverChip(label(size), size == current, "create.size.chip.${size.value}") { onPick(size) }
            }
        }
        Text(
            text = "Boards are ${sizeRangeText(sizes.map(label))}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("create.size.hint"),
        )
    }
}

/**
 * The variant table's sizes for the hint: up to four in full ("4×4, 6×6, 8×8 or 10×10", so a
 * table with gaps reads as it is), five or more as "first to last" ("4×4 to 12×12").
 */
internal fun sizeRangeText(labels: List<String>): String = when {
    labels.size <= 1 -> labels.firstOrNull().orEmpty()
    labels.size <= MaxListedSizes -> labels.dropLast(1).joinToString(", ") + " or " + labels.last()
    else -> "${labels.first()} to ${labels.last()}"
}

// Preview: the panel mid-table (7×7 checked) and at the table's top end (12×12 checked).
@PuzzleBoxPreviews
@Composable
private fun SizePanelPreview() {
    val sizes = (4..12).map { VariantId("queens/$it") }
    val label = { variant: VariantId -> variant.value.substringAfter('/').let { "$it×$it" } }
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                SizePanel(sizes, current = sizes[3], label = label, onPick = {})
                SizePanel(sizes, current = sizes.last(), label = label, onPick = {})
            }
        }
    }
}
