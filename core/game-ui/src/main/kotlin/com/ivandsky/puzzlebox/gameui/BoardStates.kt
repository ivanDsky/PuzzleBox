package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Board-level async states (A7/A8): fetches degrade to cache with a clear message; the
 * copy stays game-agnostic — screens pass their own wording when they must.
 */
@Composable
fun BoardLoading(
    modifier: Modifier = Modifier,
    label: String = "Setting up the board…",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Spacing.xxl),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BoardEmpty(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    EmptyState(title = title, message = message, modifier = modifier, actionLabel = actionLabel, onAction = onAction)
}

@Composable
fun BoardError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Couldn't load the puzzle",
    retryLabel: String = "Try again",
) {
    ErrorState(title = title, message = message, onRetry = onRetry, modifier = modifier, retryLabel = retryLabel)
}
