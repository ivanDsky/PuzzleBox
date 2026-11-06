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

/**
 * The Studio under its top bar, split by the play policy ([PlayLayout], `playLayoutFor`:
 * Expanded width and landscape). [PlayLayout.SinglePane]: the [board]'s square hangs from the
 * bar with the [toolStrip] and the [actionArea] under it; the verdict card is the pill's popover.
 * [PlayLayout.TwoPane] (02 handoff "Tablet"): the square centred in the left pane, and the
 * [StudioSidePane] on the right with the card inline ([verdict], [verdictCard], [issues]), the
 * strip and the actions. Whatever the game draws under its grid (Queens' coach line and probe
 * strip) is part of [board], so it stays under the board in both layouts.
 */
@Composable
internal fun StudioLayout(
    layout: PlayLayout,
    verdict: Verdict,
    verdictCard: VerdictCardState,
    board: @Composable (Modifier) -> Unit,
    toolStrip: @Composable (Modifier) -> Unit,
    issues: @Composable (Modifier) -> Unit,
    actionArea: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (layout) {
        PlayLayout.SinglePane -> Column(modifier.fillMaxSize()) {
            BoardSlot(
                alignment = Alignment.TopCenter,
                board = board,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg),
            )
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
                verdictCard = verdictCard,
                issues = issues,
                toolStrip = toolStrip,
                actionArea = actionArea,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

/** The square the game's board fills: the smaller of the slot's width and height. */
@Composable
private fun BoardSlot(
    alignment: Alignment,
    board: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side).testTag("create.editor.board")) {
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
        verdictCard = VerdictCardState(recheck = {}),
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
        actionArea = { areaModifier -> ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, modifier = areaModifier) },
    )
}
