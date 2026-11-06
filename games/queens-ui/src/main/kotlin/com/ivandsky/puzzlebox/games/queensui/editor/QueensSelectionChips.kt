package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queensui.LocalQueensReducedMotion

/**
 * The context bar over [controller]'s selection: **Area** spans the first to the last
 * selected cell, **All of colour** grows to every cell of each selected colour, and both
 * report the grown selection to [onGrown] (the board re-opens the ring at its centroid).
 */
@Composable
internal fun QueensSelectionChips(
    controller: QueensEditorController,
    onGrown: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection by controller.selection.collectAsState()
    QueensSelectionChips(
        count = selection.size,
        onArea = {
            controller.selection.value.let { controller.selectArea(it.first(), it.last()) }
            onGrown(controller.selection.value)
        },
        onAllOfColour = {
            val regions = (controller.draft.value.state.draft as QueensDraft).regions
            controller.selection.value.map { regions[it] }.distinct().forEach(controller::selectAllOf)
            onGrown(controller.selection.value)
        },
        onCancel = controller::cancel,
        modifier = modifier,
    )
}

/**
 * The context bar under the editor board (prototype 03 b). With nothing selected ([count] 0)
 * it shows the first-timer hint; with a selection, "{k} cells" and the chips **Area**,
 * **All of colour** and **Cancel**. It is always one line of one touch-target height, scrolling
 * sideways when the board is narrow, never wrapping, so the board above never jumps or shrinks
 * (ledger ruling, 28). The count node says k in its state description.
 */
@Composable
internal fun QueensSelectionChips(
    count: Int,
    onArea: () -> Unit,
    onAllOfColour: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A chip is its label plus padding, at least one touch target; the bar reserves that height
    // with or without chips, so a large font scale cannot make the selection push the board up.
    val label = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val chipLabel = with(LocalDensity.current) { remember(measurer, label) { measurer.measure("Area", label).size.height }.toDp() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = maxOf(LocalMinimumInteractiveComponentSize.current, chipLabel + Spacing.sm * 2))
            .testTag("queens.editor.context")
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count == 0) {
            Text(
                text = "Drag across cells, then pick a colour.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
                modifier = Modifier.testTag("queens.editor.context.hint"),
            )
            return@Row
        }
        Text(
            text = if (count == 1) "1 cell" else "$count cells",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            softWrap = false,
            modifier = Modifier
                .testTag("queens.editor.context.count")
                .semantics { stateDescription = "$count" },
        )
        // Takes the slack while the line fits (chips to the end); nothing once it scrolls.
        Spacer(Modifier.weight(1f))
        ContextChip("Area", "queens.editor.chip.area", onArea)
        ContextChip("All of colour", "queens.editor.chip.allOf", onAllOfColour)
        ContextChip("Cancel", "queens.editor.chip.cancel", onCancel)
    }
}

/** A pill on `surfaceContainerHigh` with a full touch target and Press / Tap feedback. */
@Composable
private fun ContextChip(label: String, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val still = LocalQueensReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !still) MotionTokens.PressScale else 1f,
        animationSpec = tween(MotionTokens.DurationShort),
        label = "chip-press",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag(tag),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            softWrap = false,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Nothing selected: hint", widthDp = 360, showBackground = true, group = "Queens context bar")
@Composable
private fun QueensSelectionChipsEmptyPreview() {
    PuzzleBoxTheme { QueensSelectionChips(count = 0, onArea = {}, onAllOfColour = {}, onCancel = {}) }
}

@PuzzleBoxPreviews
@Preview(name = "3 cells selected", widthDp = 360, showBackground = true, group = "Queens context bar")
@Composable
private fun QueensSelectionChipsSelectedPreview() {
    PuzzleBoxTheme { QueensSelectionChips(count = 3, onArea = {}, onAllOfColour = {}, onCancel = {}) }
}
