package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.ValidatePublishDetails
import com.ivandsky.puzzlebox.game.editor.PublishBlock

/**
 * The Studio's action area under the tool strip (02 handoff "Action area"): the one-line test
 * status — "Not tested", or "Solved in 1:42 · 0 hints" for the test [passed] on this layout —
 * over Test solve, then the readiness line: one short phrase per publish block in [blocks]
 * ("Not unique", "Title is empty"), every game-owned block read together as "Fix the board"
 * (its detail lives in the game's fix list) but those the game's publish fields fix, which read
 * as their [fieldCopy] phrase, or Ready when none remain (spec A8, A10, A25).
 * Test solve is the next step, so it is Primary until a test passes, then it reads Test again in
 * the quiet style; it is enabled only when [canTestSolve] (a Unique board of a game that exports
 * puzzles). When the Studio can publish ([publish] is its state), the [PublishDetails] blocks
 * join the readiness line; the rows themselves live in the [ToolSlot]. While the author edits
 * the board ([onShowDetails] set) the readiness line leads with a one-line summary of them,
 * [title] and author name, whose tap brings them back. [PublishCta] follows, ready once nothing
 * blocks. On the phone's single pane ([compact]) Edit board keeps only that summary and
 * [PublishCta]: the board gets the status line's, Test again's and the readiness line's height,
 * and the details bring them back. The area's height follows (DurationMedium on the standard
 * curve, at once under reduced motion), in step with the [ToolSlot].
 */
@Composable
fun ActionArea(
    passed: TestSolve?,
    canTestSolve: Boolean,
    onTestSolve: () -> Unit,
    blocks: List<PublishBlock>,
    modifier: Modifier = Modifier,
    publish: PublishSession.State? = null,
    onPublish: () -> Unit = {},
    title: String = "",
    onShowDetails: (() -> Unit)? = null,
    fieldCopy: Map<String, String> = emptyMap(),
    compact: Boolean = false,
) {
    val detailBlocks = publish?.blocks.orEmpty()
    val shown = remember(blocks, detailBlocks) { blocks + detailBlocks }
    val resize = if (rememberReducedMotion()) Modifier else Modifier.animateContentSize(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
    Column(modifier.then(resize), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (compact && publish != null && onShowDetails != null) {
            PublishDetailsSummary(title, publish.details.authorName, onShowDetails)
        } else {
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
            if (publish != null && onShowDetails != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    PublishDetailsSummary(title, publish.details.authorName, onShowDetails, Modifier.weight(1f, fill = false))
                    ReadinessLine(shown, fieldCopy, Modifier.weight(1f, fill = false))
                }
            } else {
                ReadinessLine(shown, fieldCopy, Modifier.fillMaxWidth())
            }
        }
        if (publish != null) PublishCta(publish, ready = shown.isEmpty(), onPublish = onPublish, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * One phrase per readiness entry, an evenly spaced "·" between; Ready in the accent colour when
 * nothing blocks. Not tested is left to the status line above, so it alone shows no line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadinessLine(blocks: List<PublishBlock>, fieldCopy: Map<String, String>, modifier: Modifier) {
    val entries = remember(blocks, fieldCopy) { readinessOf(blocks, fieldCopy) }
    if (entries.isEmpty() && blocks.isNotEmpty()) return
    FlowRow(modifier.testTag("create.readiness"), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (entries.isEmpty()) {
            Text(
                text = "Ready",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("create.readiness.ready"),
            )
        }
        entries.forEachIndexed { index, (tag, phrase) ->
            if (index > 0) Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = phrase,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("create.readiness.$tag"),
            )
        }
    }
}

/**
 * Board blocks first, collapsed into one entry; then each domain or [fieldCopy] block once, in
 * order, but Not tested.
 */
private fun readinessOf(blocks: List<PublishBlock>, fieldCopy: Map<String, String>): List<Pair<String, String>> {
    val (named, board) = blocks.partition { it.id in DOMAIN_COPY || it.id in fieldCopy }
    val boardEntry = if (board.isEmpty()) emptyList() else listOf(BOARD_TAG to "Fix the board")
    val listed = named.map { it.id }.distinct() - PublishBlock.NOT_TESTED
    return boardEntry + listed.map { it to (DOMAIN_COPY[it] ?: fieldCopy.getValue(it)) }
}

private const val BOARD_TAG = "board"

/**
 * Copy for every id in [com.ivandsky.puzzlebox.domain.ValidatePublish.blockIds] and
 * [com.ivandsky.puzzlebox.domain.ValidatePublishDetails.blockIds] (ReadinessCopyTest), plus the
 * Studio's own day's-cap block.
 */
private val DOMAIN_COPY: Map<String, String> = mapOf(
    PublishBlock.NOT_UNIQUE to "Not unique",
    PublishBlock.NOT_TESTED to "Not tested",
    PublishBlock.EMPTY_TITLE to "Title is empty",
    PublishBlock.TITLE_TOO_LONG to "Title is too long",
    PublishBlock.AUTHOR_TOO_LONG to "Name is too long",
    PublishBlock.NOTE_TOO_LONG to "Note is too long",
    PublishBlock.DAILY_CAP to "Daily limit reached",
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

/** Ready and signed out: Publish asks for an account first. */
@PuzzleBoxPreviews
@Composable
private fun ActionAreaSignedOutPreview() {
    PuzzleBoxTheme {
        ActionArea(
            passed = TestSolve(layoutKey = "preview", timeMs = 102_000, hints = 0),
            canTestSolve = true,
            onTestSolve = {},
            blocks = emptyList(),
            modifier = Modifier.fillMaxWidth(),
            publish = PublishSession.State(signedIn = false),
            title = "Castle",
        )
    }
}

/** Ready and signed in: Publish is enabled. */
@PuzzleBoxPreviews
@Composable
private fun ActionAreaReadyPreview() {
    PuzzleBoxTheme {
        ActionArea(
            passed = TestSolve(layoutKey = "preview", timeMs = 102_000, hints = 0),
            canTestSolve = true,
            onTestSolve = {},
            blocks = emptyList(),
            modifier = Modifier.fillMaxWidth(),
            publish = PublishSession.State(details = PublishSession.Details(authorName = "Ivan")),
            title = "Castle",
        )
    }
}

/** Edit board: the details folded into their summary over Publish. */
@PuzzleBoxPreviews
@Composable
private fun ActionAreaEditingBoardPreview() {
    PuzzleBoxTheme {
        ActionArea(
            passed = TestSolve(layoutKey = "preview", timeMs = 102_000, hints = 0),
            canTestSolve = true,
            onTestSolve = {},
            blocks = emptyList(),
            modifier = Modifier.fillMaxWidth(),
            publish = PublishSession.State(details = PublishSession.Details(authorName = "Ivan")),
            title = "Castle",
            onShowDetails = {},
        )
    }
}

/** An author name over its cap: its readiness phrase, and Publish disabled. */
@PuzzleBoxPreviews
@Composable
private fun ActionAreaDetailsTooLongPreview() {
    val author = "a".repeat(Draft.AUTHOR_MAX + 1)
    PuzzleBoxTheme {
        ActionArea(
            passed = TestSolve(layoutKey = "preview", timeMs = 102_000, hints = 0),
            canTestSolve = true,
            onTestSolve = {},
            blocks = emptyList(),
            modifier = Modifier.fillMaxWidth(),
            publish = PublishSession.State(details = PublishSession.Details(author, "", ValidatePublishDetails()(author, ""))),
            title = "Castle",
        )
    }
}

private val PREVIEW_BLOCKS = listOf(
    PublishBlock(PublishBlock.SPLIT_REGION),
    PublishBlock(PublishBlock.NOT_UNIQUE),
    PublishBlock(PublishBlock.NOT_TESTED),
)
