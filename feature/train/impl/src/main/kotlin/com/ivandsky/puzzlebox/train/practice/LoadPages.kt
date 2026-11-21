package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The smallest size a control may be touched at. */
private val TouchTarget = 48.dp

/** No puzzle on the device has a point where the technique is the easiest move: say so, and offer the way back. */
@Composable
internal fun EmptyPage(name: String, onClose: () -> Unit) {
    EmptyState(
        title = "Nothing to practice yet",
        message = "No puzzle on this device uses $name yet. Play a few dailies first.",
        modifier = Modifier.fillMaxSize().testTag("train.empty"),
        actionLabel = "Back to techniques",
        onAction = onClose,
    )
}

/** Nothing on the device has a point to practice the technique at yet: say a connection is needed once, and offer one fetch. */
@Composable
internal fun NeedsConnectionPage(name: String, onTryAgain: () -> Unit) {
    EmptyState(
        title = "Needs a connection the first time",
        message = "No puzzle on this device uses $name yet. Connect, then try again to load one.",
        modifier = Modifier.fillMaxSize().testTag("train.needsConnection"),
        actionLabel = "Try again",
        onAction = onTryAgain,
    )
}

/** The puzzle did not load: say so, and offer another one that is already on the device. */
@Composable
internal fun CantLoadPage(name: String, onTryAnother: () -> Unit) {
    EmptyState(
        title = "Can't load this puzzle right now",
        message = "Another puzzle that uses $name is already on this device.",
        modifier = Modifier.fillMaxSize().testTag("train.cantLoad"),
        actionLabel = "Try another puzzle",
        onAction = onTryAnother,
    )
}

/** Every chapter of the game's catalog is done: say so, and offer the way back. */
@Composable
internal fun FinishedPage(onClose: () -> Unit) {
    EmptyState(
        title = "All boards finished",
        message = "You have been through every technique in this game.",
        modifier = Modifier.fillMaxSize().testTag("train.finished"),
        actionLabel = "Back to techniques",
        onAction = onClose,
    )
}

/** The back arrow of a title row, with the gap that follows it. */
@Composable
internal fun BackButton(onClose: () -> Unit) {
    IconButton(onClick = onClose, Modifier.size(TouchTarget).testTag("train.back")) {
        Icon(PuzzleIcons.Back, contentDescription = "Back to techniques")
    }
    Spacer(Modifier.width(Spacing.xs))
}
