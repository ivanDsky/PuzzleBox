package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** The record write's standard error row — surfaced where the player is, retryable, never blocking (F2 §7). */
@Composable
internal fun WriteFailedRow(onRetryWrite: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("result.write_failed"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Couldn't save your record.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetryWrite, modifier = Modifier.testTag("result.retry")) { Text("Retry") }
    }
}
