package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.skeleton

/**
 * Board-level async states (A7/A8): the copy stays game-agnostic — screens pass their own wording when
 * they must. Loading holds the square a board will fill, so the board lands without moving the page (A18):
 * a quiet board-shaped surface with the sheen the cards' skeletons use, capped like a board in a wide pane.
 */
@Composable
fun BoardLoading(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .widthIn(max = PhoneBoardCap)
            .fillMaxWidth()
            .aspectRatio(1f)
            .skeleton(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
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
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = "Couldn't load the puzzle",
    retryLabel: String = "Try again",
) {
    ErrorState(title = title, message = message, onRetry = onRetry, modifier = modifier, retryLabel = retryLabel)
}
