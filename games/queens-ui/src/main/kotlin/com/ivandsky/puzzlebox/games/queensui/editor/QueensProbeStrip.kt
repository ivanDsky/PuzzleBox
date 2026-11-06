package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.games.queens.editor.QueensAnswerFit
import com.ivandsky.puzzlebox.games.queensui.QueensBrand

/**
 * The one-line probe row under the coach line (the 03 handoff's probe, re-based on saved
 * crowns): for [QueensAnswerFit.Kept] "{k} crowns: the one solution keeps them ✓", for
 * [QueensAnswerFit.Broken] "{k} crowns: no solution has all of them", on the accent container
 * (tags `queens.editor.probe.kept` / `.broken`). [QueensAnswerFit.Silent] and
 * [QueensAnswerFit.Ambiguous] (the coach's 2-answers state) leave the line's height empty.
 */
@Composable
internal fun QueensProbeStrip(fit: QueensAnswerFit, crowns: Int, modifier: Modifier = Modifier) {
    val (tag, copy) = when (fit) {
        QueensAnswerFit.Kept -> "kept" to "the one solution keeps them ✓"
        QueensAnswerFit.Broken -> "broken" to "no solution has all of them"
        QueensAnswerFit.Silent, is QueensAnswerFit.Ambiguous -> null to null
    }
    Box(modifier.fillMaxWidth()) {
        val accent = QueensBrand.accent.colors()
        Text(
            text = if (copy == null) "" else "${if (crowns == 1) "1 crown" else "$crowns crowns"}: $copy",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = accent.onContainer,
            maxLines = 1,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (tag == null) {
                Modifier
            } else {
                Modifier
                    .testTag("queens.editor.probe.$tag")
                    .background(accent.container, MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.sm)
            },
        )
    }
}

@PuzzleBoxPreviews
@Preview(name = "Kept", widthDp = 360, showBackground = true, group = "Queens probe strip")
@Composable
private fun QueensProbeStripKeptPreview() {
    PuzzleBoxTheme { QueensProbeStrip(QueensAnswerFit.Kept, crowns = 3) }
}

@PuzzleBoxPreviews
@Preview(name = "Broken", widthDp = 360, showBackground = true, group = "Queens probe strip")
@Composable
private fun QueensProbeStripBrokenPreview() {
    PuzzleBoxTheme { QueensProbeStrip(QueensAnswerFit.Broken, crowns = 7) }
}
