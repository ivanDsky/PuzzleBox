package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import com.ivandsky.puzzlebox.play.FriendsSectionHead

/**
 * New today as rows of a list that is someone else's (the Community tab's): the head with its count and the filter ⋮
 * in its corner, a line naming the filter while one is on, then the body for [state]'s phase: a spinner while loading, one full-width [CommunityRow]
 * per puzzle, or the empty, offline or refused message (the last two with Retry). Every row pads its own sides, so the
 * list needs no padding of its own; the keys start with `community:`.
 */
internal fun LazyListScope.communitySection(state: CommunityState, actions: CommunityActions) {
    val loaded = state.phase as? CommunityPhase.Loaded
    item(key = "community:head", contentType = "community.head") {
        Row(
            Modifier.padding(start = Spacing.lg, end = Spacing.sm).padding(top = Spacing.sm).testTag("community.head"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FriendsSectionHead(title = "New today", count = loaded?.rows?.size?.takeIf { it > 0 }, modifier = Modifier.weight(1f))
            CommunityFilterMenu(state.games, state.game, state.difficulty, actions.onGame, actions.onDifficulty)
        }
    }
    activeFilterLabel(state)?.let { label ->
        item(key = "community:filter", contentType = "community.filter") {
            CommunityActiveFilter(label, actions.onClearFilters, Modifier.padding(start = Spacing.lg))
        }
    }
    when (val phase = state.phase) {
        CommunityPhase.Loading -> item(key = "community:loading", contentType = "community.state") {
            Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("community.loading"))
            }
        }
        is CommunityPhase.Loaded -> if (phase.rows.isEmpty()) {
            item(key = "community:empty", contentType = "community.state") {
                EmptyState(
                    title = "No new puzzles today",
                    message = "Make one or check back later.",
                    modifier = Modifier.testTag("community.empty"),
                )
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
