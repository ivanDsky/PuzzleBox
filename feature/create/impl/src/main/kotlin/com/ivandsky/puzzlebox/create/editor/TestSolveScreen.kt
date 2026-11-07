package com.ivandsky.puzzlebox.create.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.formatDuration

/** Room for the solved line and Back to editor (titleLarge + gap + the 52 dp Primary). */
private val FooterMinHeight = Spacing.xxl * 3

/**
 * The test-solve sandbox, full screen in place of the Studio (02 handoff "Test solve screen"):
 * a compact bar with Back, one instruction line and the game's real play [board]
 * (`GamePlayUi.Board` over the sandbox controller) as the largest square that fits. Once the
 * board is solved, "Solved in m:ss" **Stamps** in (the design system's [Stamp]: scale from
 * StampFromScale on the stamp spring + fade; static under reduced motion) with Back to editor.
 * Back, the system back gesture and Back to editor all report [onBack]; an unsolved sandbox's
 * progress is dropped there.
 */
@Composable
fun TestSolveScreen(
    result: TestSolve?,
    onBack: () -> Unit,
    board: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("create.testSolve.screen"),
    ) {
        CompactTopBar(
            title = "Test solve",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("create.testSolve.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back to editor")
                }
            },
        )
        Text(
            text = "Solve it like a player. Your time is kept on the draft and never ranks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        )
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg),
            contentAlignment = Alignment.TopCenter,
        ) {
            val side = minOf(maxWidth, maxHeight)
            Box(Modifier.size(side)) { board(Modifier.fillMaxSize()) }
        }
        SolvedFooter(result = result, onBack = onBack)
    }
}

/** The footer's room is always reserved, so the board never moves when the stamp lands. */
@Composable
private fun SolvedFooter(result: TestSolve?, onBack: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = FooterMinHeight).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Stamp(visible = result != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = result?.let { "Solved in ${formatDuration(it.timeMs)}" }.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .testTag("create.testSolve.solved")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                PrimaryButton(
                    text = "Back to editor",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().testTag("create.testSolve.done"),
                )
            }
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun TestSolveScreenPlayingPreview() {
    PuzzleBoxTheme {
        TestSolveScreen(result = null, onBack = {}, board = { PreviewBoard(it) })
    }
}

@PuzzleBoxPreviews
@Composable
private fun TestSolveScreenSolvedPreview() {
    PuzzleBoxTheme {
        TestSolveScreen(
            result = TestSolve(layoutKey = "preview", timeMs = 102_000, hints = 0),
            onBack = {},
            board = { PreviewBoard(it) },
        )
    }
}

@Composable
private fun PreviewBoard(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
}
