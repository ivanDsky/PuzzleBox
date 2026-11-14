package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.resultBeat

// Index on the blocks beat: the streak row and the week dots take 0 and 1, this follows them.
private const val MOMENT_BEAT_INDEX = 2

/**
 * The line saying a streak freeze was used or earned by this solve. Enters on the Result blocks
 * beat with a scale-in, after the streak count-up has run; no haptic.
 */
@Composable
internal fun FreezeMoment(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.resultBeat(
            ResultBeats.Blocks,
            index = MOMENT_BEAT_INDEX,
            fromScale = MotionTokens.EnterScaleFrom,
        ),
    )
}
