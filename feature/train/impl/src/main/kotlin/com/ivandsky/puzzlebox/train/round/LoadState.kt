package com.ivandsky.puzzlebox.train.round

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.EmptyState

/** Nothing on the device has a step of the technique yet: say a connection is needed once, and offer one fetch. */
@Composable
internal fun RoundNeedsConnection(name: String, onTryAgain: () -> Unit) {
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
internal fun RoundCantLoad(name: String, onTryAnother: () -> Unit) {
    EmptyState(
        title = "Can't load this puzzle right now",
        message = "Another puzzle that uses $name is already on this device.",
        modifier = Modifier.fillMaxSize().testTag("train.cantLoad"),
        actionLabel = "Try another puzzle",
        onAction = onTryAnother,
    )
}
