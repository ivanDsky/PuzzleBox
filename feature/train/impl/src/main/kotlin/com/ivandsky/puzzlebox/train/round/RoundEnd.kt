package com.ivandsky.puzzlebox.train.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The round's end: how many snippets were answered Right, and the way back to the technique list. */
@Composable
internal fun RoundSummary(summary: RoundState.Summary, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().testTag("train.summary"),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${summary.right} of ${summary.total} right",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        PrimaryButton("Back to techniques", onClick = onClose)
    }
}

/** No puzzle on the device has a step of the technique: say so, and offer the way back. */
@Composable
internal fun RoundEmpty(name: String, onClose: () -> Unit) {
    EmptyState(
        title = "Nothing to practice yet",
        message = "No puzzle on this device uses $name yet. Play a few dailies first.",
        modifier = Modifier.fillMaxSize().testTag("train.empty"),
        actionLabel = "Back to techniques",
        onAction = onClose,
    )
}
