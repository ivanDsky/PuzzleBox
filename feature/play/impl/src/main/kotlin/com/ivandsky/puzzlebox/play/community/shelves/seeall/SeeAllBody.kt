package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.community.CommunityRow

/**
 * The list under the filter bar for [state]'s phase: a spinner, the offline or refused message (with Retry), or once
 * ready the count line and one [CommunityRow] per puzzle, or the no-match or empty message in their place.
 */
internal fun LazyListScope.seeAllBody(state: SeeAllUiState, actions: SeeAllActions) {
    when (val phase = state.phase) {
        SeeAllPhase.Loading -> item(key = "seeall:loading", contentType = "seeall.state") {
            Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("seeall.loading"))
            }
        }
        SeeAllPhase.Offline -> item(key = "seeall:offline", contentType = "seeall.state") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                ErrorState("You're offline", "Reconnect to see puzzles.", actions.onRetry, Modifier.testTag("seeall.offline"), retryLabel = "Retry")
            }
        }
        SeeAllPhase.Refused -> item(key = "seeall:refused", contentType = "seeall.state") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                ErrorState("Couldn't load puzzles", "Something went wrong. Try again.", actions.onRetry, Modifier.testTag("seeall.refused"), retryLabel = "Retry")
            }
        }
        is SeeAllPhase.Ready -> when {
            state.noMatch -> item(key = "seeall:nomatch", contentType = "seeall.state") { NoMatchState(state.query, actions.onClearFilters) }
            phase.rows.isEmpty() -> item(key = "seeall:empty", contentType = "seeall.state") { SeeAllEmpty(state.scope) }
            else -> {
                item(key = "seeall:count", contentType = "seeall.count") {
                    Text(
                        if (phase.rows.size == 1) "1 puzzle" else "${phase.rows.size} puzzles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm).testTag("seeall.count"),
                    )
                }
                items(phase.rows, key = { "seeall:${it.code.value}" }, contentType = { "seeall.row" }) { row ->
                    CommunityRow(
                        row = row,
                        onOpen = { actions.onOpen(row.code) },
                        onReport = { actions.onReport(row.code) },
                        modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeeAllEmpty(scope: SeeAllScope?) {
    if (scope == SeeAllScope.Friends) {
        EmptyState("No puzzles from friends yet", "Puzzles your friends send you show up here.", Modifier.testTag("seeall.empty"))
    } else {
        EmptyState("No puzzles here yet", "Check back later.", Modifier.testTag("seeall.empty"))
    }
}
