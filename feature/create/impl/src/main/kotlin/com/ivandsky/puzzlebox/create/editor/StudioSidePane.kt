package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict

/** The tablet side pane's width (02 handoff "Tablet"). */
private val SidePaneWidth = 372.dp

/**
 * The tablet Studio's right pane, 372 dp on the surface: the [VerdictCard] always open inline
 * (so there is no pill), with the witness A/B over [verdictCard]'s overlay and the game's fix
 * list ([issues]); then the [toolStrip] with its game hint; both scroll when the pane is short.
 * The [actionArea] stays pinned at the bottom.
 */
@Composable
internal fun StudioSidePane(
    verdict: Verdict,
    verdictCard: VerdictCardState,
    issues: @Composable (Modifier) -> Unit,
    toolStrip: @Composable (Modifier) -> Unit,
    actionArea: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlay by verdictCard.overlay.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .width(SidePaneWidth)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .testTag("create.studio.sidePane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            VerdictCard(
                verdict = verdict,
                onCheckAgain = verdictCard::checkAgain,
                overlay = overlay,
                onOverlay = verdictCard::choose,
                issues = issues,
                modifier = Modifier.fillMaxWidth(),
            )
            toolStrip(Modifier.fillMaxWidth())
        }
        actionArea(Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Unique", heightDp = 640, showBackground = true, group = "Studio side pane")
@Composable
private fun StudioSidePaneUniquePreview() {
    PuzzleBoxTheme { PreviewPane(Verdict.Unique(Solution(listOf(1, 3, 0, 2)))) }
}

@PuzzleBoxPreviews
@Preview(name = "Checking", heightDp = 640, showBackground = true, group = "Studio side pane")
@Composable
private fun StudioSidePaneCheckingPreview() {
    PuzzleBoxTheme { PreviewPane(Verdict.Checking) }
}

@Composable
private fun PreviewPane(verdict: Verdict) {
    StudioSidePane(
        verdict = verdict,
        verdictCard = VerdictCardState(recheck = {}),
        issues = {},
        toolStrip = { stripModifier ->
            ToolStrip(
                canUndo = true,
                canRedo = false,
                onUndo = {},
                onRedo = {},
                sizes = listOf(VariantId("queens/4")),
                size = VariantId("queens/4"),
                sizeLabel = { "4×4" },
                onResize = {},
                onClear = {},
                hint = { Text("Tap a cell to place a crown", it, style = MaterialTheme.typography.bodySmall) },
                modifier = stripModifier,
            )
        },
        actionArea = { areaModifier ->
            ActionArea(
                passed = null,
                canTestSolve = verdict is Verdict.Unique,
                onTestSolve = {},
                blocks = emptyList(),
                modifier = areaModifier,
            )
        },
        modifier = Modifier.fillMaxHeight(),
    )
}
