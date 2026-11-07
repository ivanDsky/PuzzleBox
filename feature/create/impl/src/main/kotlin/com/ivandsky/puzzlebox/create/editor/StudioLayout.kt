package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/**
 * The Studio under its top bar, split by the play policy ([PlayLayout], `playLayoutFor`:
 * Expanded width and landscape). [PlayLayout.SinglePane]: the [board] slot spans the bar to
 * the [toolStrip], with the [actionArea] under it; the verdict card is the pill's. The slot is
 * the full content width and all the height above the strip, not a square: the game sizes its
 * grid square in it and keeps its own rows (Queens' coach line and probe strip) under the grid,
 * and the group sits on the strip, so a width-bound grid leaves its spare height under the bar.
 * [PlayLayout.TwoPane] (02 handoff "Tablet"): a square centred in the left pane, and the
 * [StudioSidePane] on the right with the card inline ([verdict], [overlay], [issues]), the
 * strip and the actions. The [toast] covers no control: on the phone it sits in the board slot,
 * above the strip; on the tablet inside the side pane, above the action area.
 */
@Composable
internal fun StudioLayout(
    layout: PlayLayout,
    verdict: Verdict,
    overlay: EditorOverlay,
    onCheckAgain: () -> Unit,
    onOverlay: (EditorOverlay) -> Unit,
    board: @Composable (Modifier) -> Unit,
    toolStrip: @Composable (Modifier) -> Unit,
    issues: @Composable (Modifier) -> Unit,
    actionArea: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    toast: @Composable (Modifier) -> Unit = {},
) {
    when (layout) {
        PlayLayout.SinglePane -> Column(modifier.fillMaxSize()) {
            // No bottom padding: the game's reserved rows already part the grid from the strip.
            PhoneBoardSlot(
                board = board,
                toast = toast,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg),
            )
            toolStrip(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md))
            actionArea(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg))
        }
        PlayLayout.TwoPane -> Row(modifier.fillMaxSize()) {
            SquareBoardSlot(
                alignment = Alignment.Center,
                board = board,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(Spacing.lg),
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StudioSidePane(
                verdict = verdict,
                overlay = overlay,
                onCheckAgain = onCheckAgain,
                onOverlay = onOverlay,
                issues = issues,
                toolStrip = toolStrip,
                actionArea = actionArea,
                toast = toast,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

private const val BoardSlotTag = "create.editor.board"

/**
 * The phone's board slot: the game's group (grid and rows, [board]) measured to its own height
 * and set on the slot's bottom, so it meets the strip. The [toast] sits in the spare height above
 * the group when it fits there, clear of the rows; when the grid takes the height, at the bottom.
 */
@Composable
private fun PhoneBoardSlot(
    board: @Composable (Modifier) -> Unit,
    toast: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    Layout(
        content = {
            Box { board(Modifier.fillMaxWidth()) }
            Box { toast(Modifier) }
        },
        modifier = modifier.testTag(BoardSlotTag),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val group = measurables[0].measure(loose)
        val note = measurables[1].measure(loose)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            val groupTop = height - group.height
            group.placeRelative((width - group.width) / 2, groupTop)
            // The toast's own margin parts it from the grid; at the bottom it keeps the strip's gap too.
            val above = groupTop - note.height
            val noteTop = if (above >= 0) above else height - Spacing.sm.roundToPx() - note.height
            note.placeRelative((width - note.width) / 2, noteTop)
        }
    }
}

/** The square the game's board fills: the smaller of the slot's width and height, at [alignment]. */
@Composable
internal fun SquareBoardSlot(
    alignment: Alignment,
    board: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side).testTag(BoardSlotTag)) {
            board(Modifier.fillMaxSize())
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Tablet two-pane", widthDp = 960, heightDp = 600, showBackground = true, group = "Studio layout")
@Composable
private fun StudioLayoutTwoPanePreview() {
    PuzzleBoxTheme { PreviewLayout(PlayLayout.TwoPane) }
}

@PuzzleBoxPreviews
@Preview(name = "Phone single-pane", widthDp = 360, heightDp = 640, showBackground = true, group = "Studio layout")
@Composable
private fun StudioLayoutSinglePanePreview() {
    PuzzleBoxTheme { PreviewLayout(PlayLayout.SinglePane) }
}

@Composable
private fun PreviewLayout(layout: PlayLayout) {
    StudioLayout(
        layout = layout,
        verdict = Verdict.Checking,
        overlay = EditorOverlay(),
        onCheckAgain = {},
        onOverlay = {},
        board = { boardModifier ->
            Box(boardModifier, contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
            }
        },
        toolStrip = { stripModifier ->
            ToolStrip(
                canUndo = true,
                canRedo = false,
                onUndo = {},
                onRedo = {},
                sizes = listOf(VariantId("queens/8")),
                size = VariantId("queens/8"),
                sizeLabel = { "8×8" },
                onResize = {},
                onClear = {},
                hint = { Text("Tap a cell to place a crown", it, style = MaterialTheme.typography.bodySmall) },
                modifier = stripModifier,
            )
        },
        issues = {},
        actionArea = { areaModifier ->
            ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, blocks = emptyList(), modifier = areaModifier)
        },
    )
}
