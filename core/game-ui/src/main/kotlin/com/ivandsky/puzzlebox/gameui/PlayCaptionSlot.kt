package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** Lines the slot reserves, empty or not (A7), so the board never moves. */
private const val CAPTION_LINES = 2

/**
 * The one in-play message surface (spec §2 / A7): it always reserves [CAPTION_LINES] lines
 * of bodyMedium at the current font scale, so a caption appearing, changing or clearing
 * never moves the board. Content cross-fades on MotionTokens short; reduced motion swaps
 * instantly. One polite live-region node carries the current caption, so each change is
 * announced once (the cross-fade's two layers stay out of the semantics tree). [textAlign]
 * lets a host centre the line under a centred instruction; the daily chrome keeps Start.
 *
 * A [note] (the one-time hint cost line) takes the second line under a caption that fits on one, in a quieter colour.
 * A caption that needs both lines is never cut for it: the note waits, and [onNoteShown] is called only once the note is
 * drawn. With no caption the note sits alone.
 */
@Composable
fun PlayCaptionSlot(
    caption: String?,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    note: String? = null,
    onNoteShown: () -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val shownNote = note?.takeIf {
            caption == null || measurer.measure(
                text = caption,
                style = style,
                maxLines = CAPTION_LINES,
                constraints = Constraints(maxWidth = constraints.maxWidth),
            ).lineCount <= 1
        }
        if (shownNote != null) LaunchedEffect(shownNote) { onNoteShown() }
        Box(
            Modifier.fillMaxWidth().testTag("play.caption_slot").clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                val spoken = listOfNotNull(caption, shownNote).joinToString(". ")
                if (spoken.isNotEmpty()) text = AnnotatedString(spoken)
            },
        ) {
            if (reducedMotion) {
                CaptionText(caption, shownNote, textAlign)
            } else {
                Crossfade(
                    targetState = caption to shownNote,
                    animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
                    label = "play-caption",
                ) { (shown, shownNoteLayer) -> CaptionText(shown, shownNoteLayer, textAlign) }
            }
        }
    }
}

@Composable
private fun CaptionText(caption: String?, note: String?, textAlign: TextAlign) {
    if (note == null) {
        CaptionLines(caption.orEmpty(), CAPTION_LINES, CAPTION_LINES, textAlign)
    } else {
        // Two stacked one-line texts round differently from one two-line text, so the empty slot's own height sizes the
        // box and the lines draw over it: the board never moves.
        Box {
            CaptionLines("", CAPTION_LINES, CAPTION_LINES, textAlign, Color.Transparent)
            Column(Modifier.matchParentSize().wrapContentHeight(Alignment.Top, unbounded = true)) {
                if (caption != null) CaptionLines(caption, 1, 1, textAlign)
                val noteLines = if (caption == null) CAPTION_LINES else 1
                CaptionLines(note, noteLines, noteLines, textAlign, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CaptionLines(
    text: String,
    minLines: Int,
    maxLines: Int,
    textAlign: TextAlign,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = textAlign,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

// Previews — one per slot state (empty, one line, two lines); the slot's height is the
// same in all three.

@Preview(widthDp = 360)
@Composable
private fun PlayCaptionSlotEmptyPreview() {
    PuzzleBoxTheme(darkTheme = false) {
        Surface { PlayCaptionSlot(caption = null) }
    }
}

@Preview(widthDp = 360)
@Composable
private fun PlayCaptionSlotOneLinePreview() {
    PuzzleBoxTheme(darkTheme = false) {
        Surface { PlayCaptionSlot(caption = "Hint — the red region") }
    }
}

@Preview(widthDp = 360)
@Composable
private fun PlayCaptionSlotTwoLinesPreview() {
    PuzzleBoxTheme(darkTheme = false) {
        Surface {
            PlayCaptionSlot(
                caption = "Three suns in a row — no more than two of the same symbol may sit side by side",
            )
        }
    }
}
