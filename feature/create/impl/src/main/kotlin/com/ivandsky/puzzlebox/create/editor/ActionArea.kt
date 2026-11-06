package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration

/**
 * The Studio's action area under the tool strip (02 handoff "Action area"): the one-line test
 * status — "Not tested", or "Solved in 1:42 · 0 hints" for the test [passed] on this layout —
 * over Test solve. Test solve is the next step, so it is Primary until a test passes, then it
 * reads Test again in the quiet style; it is enabled only when [canTestSolve] (a Unique board of
 * a game that exports puzzles). M2 has no Publish button: publishing arrives in M3.
 */
@Composable
fun ActionArea(
    passed: TestSolve?,
    canTestSolve: Boolean,
    onTestSolve: () -> Unit,
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
    }
}

private fun statusOf(test: TestSolve): String =
    "Solved in ${formatDuration(test.timeMs)} · ${test.hints} ${if (test.hints == 1) "hint" else "hints"}"

@PuzzleBoxPreviews
@Composable
private fun ActionAreaUntestedPreview() {
    PuzzleBoxTheme {
        ActionArea(passed = null, canTestSolve = true, onTestSolve = {}, modifier = Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Composable
private fun ActionAreaLockedPreview() {
    PuzzleBoxTheme {
        ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, modifier = Modifier.fillMaxWidth())
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
