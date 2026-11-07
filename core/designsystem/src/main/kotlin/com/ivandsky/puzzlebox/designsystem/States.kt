package com.ivandsky.puzzlebox.designsystem

import android.content.res.Configuration
import com.airbnb.android.showkase.annotation.ShowkaseComposable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

/**
 * An error with its cause; [onRetry] null when retrying can't help, so no button is offered.
 * An [icon] names the kind of failure in a neutral disc above the title; without one, a small
 * error-coloured rule sets the tone. The block never grows past [MaxTextWidth].
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
    icon: (@Composable () -> Unit)? = null,
) {
    StateLayout(modifier.widthIn(max = MaxTextWidth)) {
        // The text stays neutral so the copy carries the meaning instead of an alarm.
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(ErrorDiscSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(ErrorIconSize), propagateMinConstraints = true) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        icon()
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(Spacing.xxl)
                    .height(Spacing.xs)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
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
        if (onRetry != null) PrimaryButton(text = retryLabel, onClick = onRetry)
    }
}

private val ErrorIconSize = Spacing.xxl
private val ErrorDiscSize = Spacing.xxl * 2

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
@ShowkaseComposable(name = "EmptyState", group = "States")
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
@ShowkaseComposable(name = "ErrorState", group = "States")
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

/** The error slot naming its failure with an icon, and no retry. */
@ShowkaseComposable(name = "ErrorState icon", group = "States")
@PuzzleBoxPreviews
@Composable
internal fun ErrorStateIconPreview() {
    PuzzleBoxTheme {
        ErrorState(
            title = "You're offline",
            message = "A new code needs a connection to open.",
            onRetry = null,
            icon = { Icon(PuzzleIcons.NeedsConnection, contentDescription = null) },
        )
    }
}
