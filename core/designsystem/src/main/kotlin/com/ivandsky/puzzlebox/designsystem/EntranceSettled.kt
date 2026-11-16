package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True under a host that covers its content while it arrives (the solve celebration over Result): the entrance
 * motion below it is skipped and every block composes in its end state, so what the host uncovers is the finished
 * page. Constant for the life of the content it wraps.
 */
val LocalEntranceSettled = staticCompositionLocalOf { false }

/** Whether entrance motion here starts in its end state: under [LocalEntranceSettled], and in a preview. */
@Composable
@ReadOnlyComposable
fun entranceSettled(): Boolean = LocalInspectionMode.current || LocalEntranceSettled.current

// Preview: a beat block under a settled entrance, in its end state from the first frame (static, A8).
@PuzzleBoxPreviews
@Composable
internal fun EntranceSettledPreview() {
    PuzzleBoxTheme {
        CompositionLocalProvider(LocalEntranceSettled provides true) {
            Text(
                text = if (entranceSettled()) "Entrance settled" else "Entrance plays",
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(Spacing.md)
                    .resultBeat(ResultBeats.Actions),
            )
        }
    }
}
