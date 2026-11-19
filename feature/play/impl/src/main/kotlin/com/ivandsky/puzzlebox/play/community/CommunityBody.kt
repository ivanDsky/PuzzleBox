package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The rows for [state]'s phase: a spinner while loading, one full-width [CommunityRow] per puzzle, or the empty,
 * offline or refused message (the last two with Retry). With [onMake] the empty message offers "Make a puzzle".
 */
internal fun LazyListScope.communityBody(state: CommunityState, actions: CommunityActions, onMake: (() -> Unit)? = null) {
    when (val phase = state.phase) {
        CommunityPhase.Loading -> item(key = "community:loading", contentType = "community.state") {
            Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("community.loading"))
            }
        }
        is CommunityPhase.Loaded -> if (phase.rows.isEmpty()) {
            if (state.game != null || state.difficulty != null) {
                item(key = "community:filter-empty", contentType = "community.state") { CommunityFilterEmpty(actions.onClearFilters) }
            } else {
                item(key = "community:empty", contentType = "community.state") {
                    EmptyState(
                        title = "No new puzzles today",
                        message = "Make one or check back later.",
                        modifier = Modifier.testTag("community.empty"),
                        actionLabel = "Make a puzzle".takeIf { onMake != null },
                        onAction = onMake,
                    )
                }
            }
        } else {
            items(phase.rows, key = { "community:${it.code.value}" }, contentType = { "community.row" }) { row ->
                CommunityRow(
                    row = row,
                    onOpen = { actions.onOpen(row.code) },
                    onReport = { actions.onReport(row.code) },
                    modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md),
                )
            }
        }
        CommunityPhase.Offline -> item(key = "community:offline", contentType = "community.state") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                ErrorState(
                    title = "You're offline",
                    message = "Reconnect to see new puzzles.",
                    onRetry = actions.onRetry,
                    retryLabel = "Retry",
                    modifier = Modifier.testTag("community.offline"),
                )
            }
        }
        CommunityPhase.Refused -> item(key = "community:refused", contentType = "community.state") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                ErrorState(
                    title = "Couldn't load new puzzles",
                    message = "Something went wrong. Try again.",
                    onRetry = actions.onRetry,
                    retryLabel = "Retry",
                    modifier = Modifier.testTag("community.refused"),
                )
            }
        }
    }
}
