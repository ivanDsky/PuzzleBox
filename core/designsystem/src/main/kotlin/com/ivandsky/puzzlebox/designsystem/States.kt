package com.ivandsky.puzzlebox.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
) {
    StateLayout(modifier) {
        // One small error-colored rule sets the tone; the text stays neutral so the
        // copy carries the meaning instead of an alarm.
        Box(
            modifier = Modifier
                .width(Spacing.xxl)
                .height(Spacing.xs)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.error),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = retryLabel, onClick = onRetry)
    }
}

@Composable
private fun StateLayout(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        content = content,
    )
}

// Previews — Studio tooling only, zero public API surface (internal, not private, so
// Showkase's generated index can access them; ticket 00 visibility findings). Exactly
// light + dark, always inside PuzzleBoxTheme (spec A1/A5). A8: neither state component
// animates — no infinite transition, no trigger — so static panes show the resting
// state.

/** The empty slot, shown with its optional action so both branches render. */
@PuzzleBoxPreviews
@Composable
internal fun EmptyStatePreview() {
    PuzzleBoxTheme {
        EmptyState(
            title = "No boards yet",
            message = "Create your first board to start building puzzles.",
            actionLabel = "New board",
            onAction = {},
        )
    }
}

/** The error slot with its retry action. */
@PuzzleBoxPreviews
@Composable
internal fun ErrorStatePreview() {
    PuzzleBoxTheme {
        ErrorState(
            title = "Something went wrong",
            message = "We couldn't load your boards. Check your connection and try again.",
            onRetry = {},
        )
    }
}
