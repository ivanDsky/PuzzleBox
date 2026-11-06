package com.ivandsky.puzzlebox.create.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict

private val ChipHeight = 24.dp
private val ChipIconSize = 14.dp

/**
 * A draft row's verdict at a glance (01 handoff, "Draft row"): a small one-line chip in the
 * verdict's tone, a check mark on Unique. Its state description is the verdict's machine name
 * ("Unique", "Checking", …) for tests and accessibility services alike. Motion: a change of
 * verdict kind is a **Crossfade** on MotionTokens short, already the reduced-motion fade.
 */
@Composable
fun VerdictChip(verdict: Verdict, modifier: Modifier = Modifier) {
    // A crossfade keyed on the verdict's kind: only a change of kind fades, not a new solution.
    AnimatedContent(
        targetState = verdict,
        modifier = modifier
            .testTag("create.draft.chip")
            .semantics { stateDescription = stateOf(verdict) },
        transitionSpec = {
            val fade = tween<Float>(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)
            fadeIn(fade) togetherWith fadeOut(fade)
        },
        label = "create-verdict-chip",
        contentKey = ::stateOf,
    ) { shown -> ChipFace(shown) }
}

@Composable
private fun ChipFace(verdict: Verdict) {
    val (container, content) = toneOf(verdict)
    Surface(
        modifier = Modifier.height(ChipHeight),
        shape = AppShapes.extraSmall,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (verdict is Verdict.Unique) {
                Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(ChipIconSize))
            }
            Text(text = labelOf(verdict), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

private fun stateOf(verdict: Verdict): String = when (verdict) {
    is Verdict.Incomplete -> "Incomplete"
    Verdict.Checking -> "Checking"
    is Verdict.Unique -> "Unique"
    is Verdict.Multiple -> "Multiple"
    is Verdict.None -> "None"
    Verdict.Undetermined -> "Undetermined"
}

private fun labelOf(verdict: Verdict): String = when (verdict) {
    is Verdict.Incomplete -> "Not checked"
    Verdict.Checking -> "Checking…"
    is Verdict.Unique -> "Unique"
    is Verdict.Multiple -> "2+ solutions"
    is Verdict.None -> "No solution"
    Verdict.Undetermined -> "Couldn't decide"
}

@Composable
private fun toneOf(verdict: Verdict): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (verdict) {
        is Verdict.Unique -> scheme.primaryContainer to scheme.onPrimaryContainer
        is Verdict.Multiple -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        is Verdict.None -> scheme.errorContainer to scheme.onErrorContainer
        is Verdict.Incomplete, Verdict.Checking, Verdict.Undetermined ->
            scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
}

@PuzzleBoxPreviews
@Composable
private fun VerdictChipPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                VerdictChip(Verdict.Incomplete(IncompleteReason("queens.unpainted", 16)))
                VerdictChip(Verdict.Checking)
                VerdictChip(Verdict.Unique(Solution(listOf(1, 7, 8, 14))))
                VerdictChip(Verdict.Multiple(Solution(emptyList()), Solution(emptyList()), emptySet()))
                VerdictChip(Verdict.None(emptySet()))
                VerdictChip(Verdict.Undetermined)
            }
        }
    }
}
