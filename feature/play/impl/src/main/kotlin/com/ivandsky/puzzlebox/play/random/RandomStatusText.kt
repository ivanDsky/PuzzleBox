package com.ivandsky.puzzlebox.play.random

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** The one plain sentence for each reason a pick said nothing. */
internal fun RandomMessage.text(): String = when (this) {
    RandomMessage.Offline -> "You're offline. Try again when you're back."
    RandomMessage.Throttled -> "Too many tries. Try again in a moment."
    RandomMessage.NothingLeftPast -> "You've solved every past puzzle here. Turn off \"Not solved yet\" to replay one."
    RandomMessage.NothingLeftCommunity -> "No new community puzzles right now."
    RandomMessage.NoneOpened -> "Couldn't open a puzzle just now. Try again."
    RandomMessage.Refused -> "Couldn't load community puzzles. Try again."
}

/** A pick that went wrong (it could not load or open), not one that found nothing to pick: the radio rows' error ring. */
internal fun RandomMessage.isFailure(): Boolean = when (this) {
    RandomMessage.NothingLeftPast, RandomMessage.NothingLeftCommunity -> false
    RandomMessage.Offline, RandomMessage.Throttled, RandomMessage.NoneOpened, RandomMessage.Refused -> true
}

/** The status line under the Pick button: [message] in plain words. */
@Composable
internal fun RandomStatusText(message: RandomMessage, modifier: Modifier = Modifier) {
    Text(
        text = message.text(),
        modifier = modifier.fillMaxWidth().testTag("random.status"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
