package com.ivandsky.puzzlebox.create.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.PublishOutcome
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The last publish's [outcome] under Publish: the share code once published, else why it wasn't.
 * Offline, the day's cap and a refusal are [PublishBanner]'s to say, above Publish.
 */
@Composable
internal fun PublishedTicket(outcome: PublishOutcome, modifier: Modifier = Modifier) {
    when (outcome) {
        is PublishOutcome.Published -> Text(
            text = "Code ${outcome.code.display}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.testTag("create.published.code"),
        )
        PublishOutcome.SignedOut -> Notice("Sign in to publish.", "create.publish.signedOut", modifier)
        is PublishOutcome.Blocked -> Notice("This draft isn't ready to publish.", "create.publish.failed", modifier)
        PublishOutcome.Offline, PublishOutcome.DailyCapReached, is PublishOutcome.Rejected -> Unit
    }
}

@Composable
private fun Notice(text: String, tag: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag(tag),
    )
}

@PuzzleBoxPreviews
@Composable
private fun PublishedTicketPreview() {
    PuzzleBoxTheme {
        PublishedTicket(PublishOutcome.Published(checkNotNull(ShareCode.parse("K7Q2MX"))))
    }
}

@PuzzleBoxPreviews
@Composable
private fun PublishedTicketSignedOutPreview() {
    PuzzleBoxTheme {
        PublishedTicket(PublishOutcome.SignedOut)
    }
}
