package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
 */
@Composable
fun PlayCaptionSlot(caption: String?, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    val reducedMotion = rememberReducedMotion()
    Box(
        modifier
            .fillMaxWidth()
            .testTag("play.caption_slot")
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                if (caption != null) text = AnnotatedString(caption)
            },
    ) {
        if (reducedMotion) {
            CaptionText(caption, textAlign)
        } else {
            Crossfade(
                targetState = caption,
                animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
                label = "play-caption",
            ) { shown -> CaptionText(shown, textAlign) }
        }
    }
}

@Composable
private fun CaptionText(caption: String?, textAlign: TextAlign) {
    Text(
        text = caption.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        minLines = CAPTION_LINES,
        maxLines = CAPTION_LINES,
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
