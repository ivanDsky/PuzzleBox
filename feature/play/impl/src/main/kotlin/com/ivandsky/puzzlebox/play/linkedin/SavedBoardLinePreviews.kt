package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.SavedBoardState
import java.time.ZoneOffset

// 2026-11-04 14:12 UTC, in a fixed zone so a preview reads the same everywhere.
private const val READ_AT_MS = 1_793_801_520_000L

@Composable
private fun PreviewLines(vararg states: SavedBoardState) {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            states.forEach { SavedBoardLine(it, zone = ZoneOffset.UTC) }
        }
    }
}

// Preview: a board read after its day ended, so it is final.
@PuzzleBoxPreviews
@Composable
internal fun SavedBoardLineFinalPreview() = PreviewLines(SavedBoardState.Saved(READ_AT_MS, final = true))

// Preview: a board read before its day ended, so it may still be missing people.
@PuzzleBoxPreviews
@Composable
internal fun SavedBoardLinePartialPreview() = PreviewLines(SavedBoardState.Saved(READ_AT_MS, final = false))

// Preview: a day outside the days LinkedIn serves, with no board kept.
@PuzzleBoxPreviews
@Composable
internal fun SavedBoardLineNeverSavedPreview() = PreviewLines(SavedBoardState.NeverSaved)

// Preview: a day LinkedIn can still serve, not read yet: no line, the sync chip's read is the whole state.
@PuzzleBoxPreviews
@Composable
internal fun SavedBoardLineNotYetReadPreview() = PreviewLines(SavedBoardState.NotYetRead)
