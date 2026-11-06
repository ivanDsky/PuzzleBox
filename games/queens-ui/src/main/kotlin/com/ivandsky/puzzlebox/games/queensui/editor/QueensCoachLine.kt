package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

/**
 * The one-line coach row under the Queens editor board (flow C): the [state]'s copy, ellipsized,
 * tagged `queens.editor.coach.<place|paint|ambiguous|unique>`. [QueensCoachState.Hidden] keeps
 * the line's height empty, so the board above never resizes. A state change crossfades
 * ([MotionTokens.DurationShort]); [reducedMotion] swaps it instantly.
 */
@Composable
internal fun QueensCoachLine(state: QueensCoachState, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = state,
        modifier = modifier.fillMaxWidth(),
        animationSpec = if (reducedMotion) snap() else tween(MotionTokens.DurationShort),
        label = "queensCoach",
    ) { shown ->
        val tag = tagOf(shown)
        Text(
            text = copyOf(shown),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (shown == QueensCoachState.Unique) FontWeight.SemiBold else FontWeight.Normal,
            color = if (shown == QueensCoachState.Unique) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().then(if (tag == null) Modifier else Modifier.testTag("queens.editor.coach.$tag")),
        )
    }
}

private fun tagOf(state: QueensCoachState): String? = when (state) {
    QueensCoachState.Hidden -> null
    is QueensCoachState.PlaceCrowns -> "place"
    QueensCoachState.PaintRegions -> "paint"
    QueensCoachState.Ambiguous -> "ambiguous"
    QueensCoachState.Unique -> "unique"
}

private fun copyOf(state: QueensCoachState): String = when (state) {
    QueensCoachState.Hidden -> ""
    is QueensCoachState.PlaceCrowns -> "Place ${state.total} crowns where the answer goes (${state.placed}/${state.total})"
    QueensCoachState.PaintRegions -> "Paint a region around each crown — drag out from a crown"
    QueensCoachState.Ambiguous -> "2 answers fit — ghost crowns show the other one. Move a highlighted cell to another colour"
    QueensCoachState.Unique -> "Unique ✓ — try solving it"
}

@PuzzleBoxPreviews
@Preview(name = "Place crowns", widthDp = 360, showBackground = true, group = "Queens coach line")
@Composable
private fun QueensCoachLinePlacePreview() {
    PuzzleBoxTheme { QueensCoachLine(QueensCoachState.PlaceCrowns(3, 7), reducedMotion = true) }
}

@PuzzleBoxPreviews
@Preview(name = "Paint regions", widthDp = 360, showBackground = true, group = "Queens coach line")
@Composable
private fun QueensCoachLinePaintPreview() {
    PuzzleBoxTheme { QueensCoachLine(QueensCoachState.PaintRegions, reducedMotion = true) }
}

@PuzzleBoxPreviews
@Preview(name = "2 answers fit (ellipsized)", widthDp = 320, showBackground = true, group = "Queens coach line")
@Composable
private fun QueensCoachLineAmbiguousPreview() {
    PuzzleBoxTheme { QueensCoachLine(QueensCoachState.Ambiguous, reducedMotion = true) }
}

@PuzzleBoxPreviews
@Preview(name = "Unique", widthDp = 360, showBackground = true, group = "Queens coach line")
@Composable
private fun QueensCoachLineUniquePreview() {
    PuzzleBoxTheme { QueensCoachLine(QueensCoachState.Unique, reducedMotion = true) }
}
