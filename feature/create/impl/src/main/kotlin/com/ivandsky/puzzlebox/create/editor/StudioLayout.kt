package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * Expanded width and landscape). [PlayLayout.SinglePane]: the [board] slot hangs from the bar
 * with the [toolStrip] and the [actionArea] under it; the verdict card is the pill's. The slot is
 * the full content width and all the height above the strip, not a square: the game sizes its
 * grid square in it and keeps its own rows (Queens' coach line and probe strip) under the grid.
 * [PlayLayout.TwoPane] (02 handoff "Tablet"): a square centred in the left pane, and the
 * [StudioSidePane] on the right with the card inline ([verdict], [overlay], [issues]), the
 * strip and the actions. The [toast] covers no control: on the phone it sits at the bottom of
 * the board slot, in the room the game leaves under its grid and rows, above the strip; on the
 * tablet inside the side pane, above the action area.
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // No bottom padding: the game's reserved rows already part the grid from the strip.
                Box(Modifier.fillMaxSize().padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg).testTag(BoardSlotTag)) {
                    board(Modifier.fillMaxSize())
                }
                // Into the room under the game's rows, which is often one toast high.
                toast(Modifier.align(Alignment.BottomCenter).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm))
            }
            toolStrip(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md))
            actionArea(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg))
        }
        PlayLayout.TwoPane -> Row(modifier.fillMaxSize()) {
            BoardSlot(
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

/** The square the game's board fills: the smaller of the slot's width and height. */
@Composable
private fun BoardSlot(
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
            Box(boardModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
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
