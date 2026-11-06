package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.editor.PublishBlock

/**
 * The Studio's action area under the tool strip (02 handoff "Action area"): the one-line test
 * status — "Not tested", or "Solved in 1:42 · 0 hints" for the test [passed] on this layout —
 * over Test solve, then the readiness line: one short phrase per publish block in [blocks]
 * ("Not unique", "Title is empty"), every game-owned block read together as "Fix the board"
 * (its detail lives in the game's fix list), or Ready when none remain (spec A8, A10, A25).
 * Test solve is the next step, so it is Primary until a test passes, then it reads Test again in
 * the quiet style; it is enabled only when [canTestSolve] (a Unique board of a game that exports
 * puzzles). M2 has no Publish button: publishing arrives in M3.
 */
@Composable
fun ActionArea(
    passed: TestSolve?,
    canTestSolve: Boolean,
    onTestSolve: () -> Unit,
    blocks: List<PublishBlock>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = passed?.let(::statusOf) ?: "Not tested",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("create.action.testStatus"),
        )
        val buttonModifier = Modifier.fillMaxWidth().testTag("create.action.testSolve")
        if (passed == null) {
            PrimaryButton(text = "Test solve", onClick = onTestSolve, enabled = canTestSolve, modifier = buttonModifier)
        } else {
            SecondaryButton(text = "Test again", onClick = onTestSolve, enabled = canTestSolve, modifier = buttonModifier)
        }
        ReadinessLine(blocks)
    }
}

/** One phrase per readiness entry, dot-separated; Ready in the accent colour when nothing blocks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadinessLine(blocks: List<PublishBlock>) {
    val entries = remember(blocks) { readinessOf(blocks) }
    FlowRow(Modifier.fillMaxWidth().testTag("create.readiness"), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (entries.isEmpty()) {
            Text(
                text = "Ready",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("create.readiness.ready"),
            )
        }
        entries.forEachIndexed { index, (tag, phrase) ->
            Text(
                text = if (index == 0) phrase else "· $phrase",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("create.readiness.$tag"),
            )
        }
    }
}

/** Board blocks first, collapsed into one entry; then each domain block once, in order. */
private fun readinessOf(blocks: List<PublishBlock>): List<Pair<String, String>> {
    val (domain, board) = blocks.partition { it.id in DOMAIN_COPY }
    val boardEntry = if (board.isEmpty()) emptyList() else listOf(BOARD_TAG to "Fix the board")
    return boardEntry + domain.map { it.id }.distinct().map { it to DOMAIN_COPY.getValue(it) }
}

private const val BOARD_TAG = "board"

/** Copy for every id in [com.ivandsky.puzzlebox.domain.ValidatePublish.blockIds] (ReadinessCopyTest). */
private val DOMAIN_COPY: Map<String, String> = mapOf(
    PublishBlock.NOT_UNIQUE to "Not unique",
    PublishBlock.NOT_TESTED to "Not tested",
    PublishBlock.EMPTY_TITLE to "Title is empty",
    PublishBlock.TITLE_TOO_LONG to "Title is too long",
)

private fun statusOf(test: TestSolve): String =
    "Solved in ${formatDuration(test.timeMs)} · ${test.hints} ${if (test.hints == 1) "hint" else "hints"}"

@PuzzleBoxPreviews
@Composable
private fun ActionAreaUntestedPreview() {
    PuzzleBoxTheme {
        ActionArea(passed = null, canTestSolve = true, onTestSolve = {}, blocks = PREVIEW_BLOCKS, modifier = Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Composable
private fun ActionAreaLockedPreview() {
    PuzzleBoxTheme {
        ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, blocks = PREVIEW_BLOCKS, modifier = Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Composable
private fun ActionAreaPassedPreview() {
    PuzzleBoxTheme {
        ActionArea(
            passed = TestSolve(layoutKey = "preview", timeMs = 102_000, hints = 0),
            canTestSolve = true,
            onTestSolve = {},
            blocks = emptyList(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val PREVIEW_BLOCKS = listOf(
    PublishBlock(PublishBlock.SPLIT_REGION),
    PublishBlock(PublishBlock.NOT_UNIQUE),
    PublishBlock(PublishBlock.NOT_TESTED),
)
