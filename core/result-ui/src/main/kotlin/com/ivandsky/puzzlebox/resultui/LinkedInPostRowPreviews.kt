package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.LinkedInPostRowState
import com.ivandsky.puzzlebox.resultui.replay.WatchReplayRow

// Previews of LinkedInPostRow — internal, light + dark inside PuzzleBoxTheme: every state, in enum order, at a phone's width,
// then the states without a time, and the rows under the cards on a phone and across a tablet's two columns.

@PuzzleBoxPreviews
@Composable
internal fun LinkedInPostRowStatesPreview() {
    PuzzleBoxTheme {
        Column(Modifier.width(360.dp).padding(Spacing.lg)) {
            for (state in LinkedInPostRowState.entries) LinkedInPostRow(state, time = "0:58", onClick = {})
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun LinkedInPostRowNoTimePreview() {
    PuzzleBoxTheme {
        Column(Modifier.width(360.dp).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (state in listOf(LinkedInPostRowState.Post, LinkedInPostRowState.Posting, LinkedInPostRowState.Posted)) {
                LinkedInPostRow(state, time = null, onClick = {})
            }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultBelowCardRowsPhonePreview() {
    PuzzleBoxTheme {
        Column(Modifier.width(360.dp).padding(Spacing.lg)) {
            ResultBelowCardRows(LinkedInPostRowState.Post, "0:58", onPost = {}, replay = { PreviewReplayRow(it) })
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ResultBelowCardRowsTabletPreview() {
    PuzzleBoxTheme {
        Column(Modifier.width(1000.dp).padding(Spacing.lg)) {
            ResultBelowCardRows(LinkedInPostRowState.Check, "0:58", onPost = {}, replay = { PreviewReplayRow(it) }, columnGap = Spacing.lg)
        }
    }
}

@Composable
private fun PreviewReplayRow(hairline: Boolean) {
    WatchReplayRow(moves = 31, durationMs = 58_000L, onClick = {}, hairline = hairline)
}
