package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Studio's Publish over the last publish's outcome. Enabled once the draft is [ready] and no
 * publish runs; signed out it reads "Sign in & publish": the tap signs in first, then publishes.
 */
@Composable
internal fun PublishCta(state: PublishSession.State, ready: Boolean, onPublish: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton(
            text = if (state.signedIn) "Publish" else "Sign in & publish",
            onClick = onPublish,
            enabled = ready && !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("create.action.publish"),
        )
        state.outcome?.let { PublishedTicket(it) }
    }
}
