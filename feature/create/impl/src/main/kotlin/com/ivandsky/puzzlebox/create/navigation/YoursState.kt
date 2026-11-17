package com.ivandsky.puzzlebox.create.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * How many puzzles are yours (every draft and every published one) and the share code of the first published,
 * newest, puzzle; null when none is published. [current] reads the same [com.ivandsky.puzzlebox.create.CreateViewModel]
 * the Custom list shows, so a count and its list never disagree.
 */
data class YoursState(val count: Int, val firstPublishedCode: String?) {
    companion object {
        /** The present state, read in composition; 0 and no code until the stored puzzles have loaded. */
        @Composable
        fun current(): YoursState {
            val state by rememberCreateViewModel().uiState.collectAsStateWithLifecycle()
            return YoursState(
                count = state.draftCount + state.published.size,
                firstPublishedCode = state.published.firstOrNull()?.code?.value,
            )
        }
    }
}
