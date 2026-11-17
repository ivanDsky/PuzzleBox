package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

// Previews — Studio tooling only (internal for Showkase's generated index). The solve moment B (M12-10) frozen at three
// points of its clock, and the end state a preview plays straight to. A8: every pane is static.

/** 560 ms after the solve: the board at the peak of its pop, the frame line just started. */
@ShowkaseComposable(name = "SolveMoment pop", group = "Motion")
@PuzzleBoxPreviews
@Composable
internal fun SolveMomentPopPreview() {
    SolveMomentPane(remember { SolveMomentState(atMs = 560) })
}

/** 700 ms: the comet turns the first corner. */
@ShowkaseComposable(name = "SolveMoment corner", group = "Motion")
@PuzzleBoxPreviews
@Composable
internal fun SolveMomentCornerPreview() {
    SolveMomentPane(remember { SolveMomentState(atMs = 700) })
}

/** 1000 ms: the line is past half way round. */
@ShowkaseComposable(name = "SolveMoment lap", group = "Motion")
@PuzzleBoxPreviews
@Composable
internal fun SolveMomentLapPreview() {
    SolveMomentPane(remember { SolveMomentState(atMs = 1000) })
}

/** The closed frame: where the moment rests from 1300 ms, and all that reduced motion shows. */
@ShowkaseComposable(name = "SolveMoment", group = "Motion")
@PuzzleBoxPreviews
@Composable
internal fun SolveMomentPreview() {
    val moment = rememberSolveMomentState()
    LaunchedEffect(moment) { moment.play() }
    SolveMomentPane(moment)
}

@Composable
private fun SolveMomentPane(moment: SolveMomentState) {
    PuzzleBoxTheme {
        val shape = MaterialTheme.shapes.large
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md)) {
            Box(
                Modifier
                    .size(160.dp)
                    .solveFrame(moment, AccentSlot.Blue.colors().accent, shape)
                    .background(MaterialTheme.colorScheme.surface, shape),
            )
        }
    }
}
