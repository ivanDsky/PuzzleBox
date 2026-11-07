package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.IntSize
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.domain.ValidatePublishDetails
import com.ivandsky.puzzlebox.game.editor.PublishBlock

/**
 * The Studio's action area under the tool strip (02 handoff "Action area"): the one-line test
 * status — "Not tested", or "Solved in 1:42 · 0 hints" for the test [passed] on this layout —
 * over Test solve, then the readiness line: one short phrase per publish block in [blocks]
 * ("Not unique", "Title is empty"), every game-owned block read together as "Fix the board"
 * (its detail lives in the game's fix list), or Ready when none remain (spec A8, A10, A25).
 * Test solve is the next step, so it is Primary until a test passes, then it reads Test again in
 * the quiet style; it is enabled only when [canTestSolve] (a Unique board of a game that exports
 * puzzles). When the Studio can publish ([publish] is its state), a passed test opens the
 * [PublishDetails] rows — [title], author name and note — until the puzzle is published; their
 * blocks join the readiness line. Unless [detailsInline] the rows live in the phone's
 * [ToolSlot] instead; while the author edits the board ([onShowDetails] set) the readiness line
 * leads with a one-line summary of them whose tap brings them back. [PublishCta] follows, ready
 * once nothing blocks.
 *
 * Motion: the details **Expand** in with a **Fade** (EnterDuration on the emphasized decelerate
 * curve) and collapse the same way once published; under reduced motion they only fade.
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
    onRename: (String) -> Unit = {},
    onAuthorName: (String) -> Unit = {},
    onNote: (String) -> Unit = {},
    detailsInline: Boolean = true,
    onShowDetails: (() -> Unit)? = null,
) {
    val detailBlocks = publish?.details?.blocks.orEmpty()
    val shown = remember(blocks, detailBlocks) { blocks + detailBlocks }
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
        if (publish != null && detailsInline) {
            DetailsReveal(visible = passed != null && publish.outcome !is PublishOutcome.Published) {
                PublishDetails(
                    title = title,
                    details = publish.details,
                    signedIn = publish.signedIn,
                    onRename = onRename,
                    onAuthorName = onAuthorName,
                    onNote = onNote,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !publish.busy,
                )
            }
        }
        if (publish != null && !detailsInline && onShowDetails != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PublishDetailsSummary(title, publish.details.authorName, onShowDetails, Modifier.weight(1f, fill = false))
                ReadinessLine(shown, Modifier.weight(1f, fill = false))
            }
        } else {
            ReadinessLine(shown, Modifier.fillMaxWidth())
        }
        if (publish != null) PublishCta(publish, ready = shown.isEmpty(), onPublish = onPublish, modifier = Modifier.fillMaxWidth())
    }
}

/** Shows [content] once a test passes, expanding it in; reduced motion only fades. */
@Composable
private fun DetailsReveal(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val spec = tween<IntSize>(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
    val fade = tween<Float>(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.EnterDuration)
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) fadeIn(fade) else expandVertically(spec) + fadeIn(fade),
        exit = if (reduced) fadeOut(fade) else shrinkVertically(spec) + fadeOut(fade),
    ) {
        content()
    }
}

/**
 * One phrase per readiness entry, an evenly spaced "·" between; Ready in the accent colour when
 * nothing blocks. Not tested is left to the status line above, so it alone shows no line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadinessLine(blocks: List<PublishBlock>, modifier: Modifier) {
    val entries = remember(blocks) { readinessOf(blocks) }
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

/** Board blocks first, collapsed into one entry; then each domain block once, in order, but Not tested. */
private fun readinessOf(blocks: List<PublishBlock>): List<Pair<String, String>> {
    val (domain, board) = blocks.partition { it.id in DOMAIN_COPY }
    val boardEntry = if (board.isEmpty()) emptyList() else listOf(BOARD_TAG to "Fix the board")
    val listed = domain.map { it.id }.distinct() - PublishBlock.NOT_TESTED
    return boardEntry + listed.map { it to DOMAIN_COPY.getValue(it) }
}

private const val BOARD_TAG = "board"

/**
 * Copy for every id in [com.ivandsky.puzzlebox.domain.ValidatePublish.blockIds] and
 * [com.ivandsky.puzzlebox.domain.ValidatePublishDetails.blockIds] (ReadinessCopyTest).
 */
private val DOMAIN_COPY: Map<String, String> = mapOf(
    PublishBlock.NOT_UNIQUE to "Not unique",
    PublishBlock.NOT_TESTED to "Not tested",
    PublishBlock.EMPTY_TITLE to "Title is empty",
    PublishBlock.TITLE_TOO_LONG to "Title is too long",
    PublishBlock.AUTHOR_TOO_LONG to "Name is too long",
    PublishBlock.NOTE_TOO_LONG to "Note is too long",
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

/** Ready and signed in: the details carry the display name, and Publish is enabled. */
@PuzzleBoxPreviews
@Composable
private fun ActionAreaDetailsPreview() {
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

/** A phone's Edit board: the details folded into their summary over Publish. */
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
            detailsInline = false,
            onShowDetails = {},
        )
    }
}

/** An author name over its cap: the inline error, its readiness phrase, and Publish disabled. */
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
