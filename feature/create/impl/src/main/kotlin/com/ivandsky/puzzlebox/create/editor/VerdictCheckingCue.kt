package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlinx.coroutines.delay

/** How long a check runs before the card shows it is still working, so a quick check never flashes the cue. */
internal const val CHECKING_CUE_GRACE_MS = 400L

/** The reduced-motion cue's still segment, centred on the track. */
private const val STILL_SEGMENT_FRACTION = 1f / 3f

/**
 * The verdict card's "still working" cue, a bar [Spacing.xs] high: past [CHECKING_CUE_GRACE_MS]
 * while [running], M3's indeterminate linear indicator under `create.verdict.progress`; under
 * [reducedMotion] a still segment on the same track instead, without progress semantics, as it
 * does not move (the title already says Checking). Nothing before the grace or once [running] ends.
 */
@Composable
internal fun VerdictCheckingCue(running: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    var graced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(CHECKING_CUE_GRACE_MS)
        graced = true
    }
    if (!running || !graced) return
    val cue = modifier.fillMaxWidth().height(Spacing.xs).testTag("create.verdict.progress")
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    if (reducedMotion) {
        Box(cue.clip(CircleShape).background(track), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(STILL_SEGMENT_FRACTION).fillMaxHeight().clip(CircleShape).background(color))
        }
    } else {
        LinearProgressIndicator(cue, color = color, trackColor = track)
    }
}
