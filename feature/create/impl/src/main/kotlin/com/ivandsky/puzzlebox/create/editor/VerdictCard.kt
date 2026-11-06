package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict

private val CardMaxWidth = 320.dp
private val GradeIconSize = 16.dp

/** The grade's machine name on a Unique: its level ("Medium"), "NeedsGuessing", or null for a game without a ladder. */
internal fun Verdict.Unique.gradeState(): String? = when {
    needsGuessing -> "NeedsGuessing"
    else -> difficulty?.level?.name
}

/** The grade as the author reads it: "Medium", "needs guessing", or null. */
internal fun Verdict.Unique.gradeLabel(): String? = when {
    needsGuessing -> "needs guessing"
    else -> difficulty?.level?.name
}

/**
 * The verdict in full (02 handoff, "Pill popover"): title, subtitle, the grade of a Unique
 * (its difficulty, or the Needs guessing flag, under `create.verdict.grade` with the grade's
 * machine name as state), a line on what the verdict means and, after Undetermined,
 * Check again ([onCheckAgain]). Motion: a new verdict **Crossfade**s in on MotionTokens
 * medium, emphasized decelerate; under reduced motion a plain 120 ms fade.
 */
@Composable
fun VerdictCard(verdict: Verdict, onCheckAgain: () -> Unit, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    Surface(
        modifier = modifier.widthIn(max = CardMaxWidth).testTag("create.verdict.card"),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        AnimatedContent(
            targetState = verdict,
            transitionSpec = {
                val fade = if (reducedMotion) {
                    tween<Float>(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingStandard)
                } else {
                    tween<Float>(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)
                }
                fadeIn(fade) togetherWith fadeOut(fade)
            },
            label = "create-verdict-card",
            contentKey = ::faceKey,
        ) { shown -> CardFace(shown, onCheckAgain) }
    }
}

@Composable
private fun CardFace(verdict: Verdict, onCheckAgain: () -> Unit) {
    Column(
        modifier = Modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(titleOf(verdict), style = MaterialTheme.typography.titleMedium)
        if (verdict is Verdict.Unique && verdict.gradeState() != null) {
            Grade(verdict)
        } else {
            Text(
                text = subtitleOf(verdict),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = explanationOf(verdict),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (verdict == Verdict.Undetermined) {
            SecondaryButton(
                text = "Check again",
                onClick = onCheckAgain,
                modifier = Modifier.fillMaxWidth().testTag("create.verdict.checkAgain"),
            )
        }
    }
}

/** A difficulty in the Unique tone, or the Needs guessing flag in the warn tone. */
@Composable
private fun Grade(verdict: Verdict.Unique) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = if (verdict.needsGuessing) {
        scheme.tertiaryContainer to scheme.onTertiaryContainer
    } else {
        scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier
            .testTag("create.verdict.grade")
            .semantics { stateDescription = verdict.gradeState().orEmpty() },
        shape = AppShapes.extraSmall,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (verdict.needsGuessing) {
                Icon(PuzzleIcons.Hint, contentDescription = null, modifier = Modifier.size(GradeIconSize))
                Text("Needs guessing", style = MaterialTheme.typography.labelLarge)
            } else {
                Text(verdict.gradeLabel().orEmpty(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Crossfade only when what the card says changes: the verdict's kind, its grade, or its clash count. */
private fun faceKey(verdict: Verdict): Any = when (verdict) {
    is Verdict.Unique -> "Unique/${verdict.gradeState()}"
    is Verdict.Multiple -> "Multiple/${verdict.differingCells.size}"
    is Verdict.None -> "None/${verdict.conflicts.size}"
    else -> verdict::class
}

private fun titleOf(verdict: Verdict): String = when (verdict) {
    is Verdict.Incomplete -> "Not checked yet"
    Verdict.Checking -> "Checking…"
    is Verdict.Unique -> "Unique ✓"
    is Verdict.Multiple -> "2+ solutions"
    is Verdict.None -> "No solution"
    Verdict.Undetermined -> "Couldn't decide"
}

private fun subtitleOf(verdict: Verdict): String = when (verdict) {
    is Verdict.Incomplete -> "Finish the board and it checks itself"
    Verdict.Checking -> "Runs a moment after your last tap"
    is Verdict.Unique -> "Exactly one solution"
    is Verdict.Multiple -> "${verdict.differingCells.size} cells differ, marked on the board"
    is Verdict.None ->
        if (verdict.conflicts.isEmpty()) "No single spot to blame" else "${verdict.conflicts.size} clashing cells marked in red"
    Verdict.Undetermined -> "The solver hit its time budget"
}

private fun explanationOf(verdict: Verdict): String = when (verdict) {
    is Verdict.Incomplete -> "The verdict appears once every cell is set."
    Verdict.Checking -> "The board stays editable while it checks."
    is Verdict.Unique -> when {
        verdict.needsGuessing ->
            "Exactly one solution, but logic alone stalls: players would have to guess and check. " +
                "Publishing stays blocked until logic can finish it."
        verdict.difficulty != null -> "Exactly one layout fits, and logic alone solves it."
        else -> "Exactly one layout fits."
    }
    is Verdict.Multiple -> "At least two layouts work. Change one of the marked cells so only one fits."
    is Verdict.None ->
        if (verdict.conflicts.isEmpty()) {
            "Every layout breaks a rule somewhere, but no single spot is to blame. Try reshaping part of the board."
        } else {
            "The marked cells can't all hold together. Change one of them."
        }
    Verdict.Undetermined ->
        "The checker ran out of time before it could prove one answer. Your draft is saved. " +
            "Check again, or simplify the board."
}

private object CardPreviewRung : LadderRung {
    override val id: String = "preview.lineLock"
    override val level: Difficulty.Level = Difficulty.Level.Medium
    override val captionKey: String = "preview.lineLock"
}

@PuzzleBoxPreviews
@Composable
private fun VerdictCardUniquePreview() {
    CardPreview(Verdict.Unique(Solution(listOf(1, 7, 8, 14)), Difficulty(CardPreviewRung)))
}

@PuzzleBoxPreviews
@Composable
private fun VerdictCardNeedsGuessingPreview() {
    CardPreview(Verdict.Unique(Solution(listOf(1, 7, 8, 14)), needsGuessing = true))
}

@PuzzleBoxPreviews
@Composable
private fun VerdictCardUndeterminedPreview() {
    CardPreview(Verdict.Undetermined)
}

@PuzzleBoxPreviews
@Composable
private fun VerdictCardOtherStatesPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                VerdictCard(Verdict.Incomplete(IncompleteReason("queens.unpainted", 16)), onCheckAgain = {})
                VerdictCard(Verdict.Checking, onCheckAgain = {})
                VerdictCard(Verdict.Multiple(Solution(emptyList()), Solution(emptyList()), setOf(1, 2, 5, 6)), onCheckAgain = {})
                VerdictCard(Verdict.None(setOf(3, 4)), onCheckAgain = {})
            }
        }
    }
}

@Composable
private fun CardPreview(verdict: Verdict) {
    PuzzleBoxTheme {
        Surface {
            VerdictCard(verdict, onCheckAgain = {}, modifier = Modifier.padding(Spacing.lg))
        }
    }
}
