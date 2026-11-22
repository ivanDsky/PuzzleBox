package com.ivandsky.puzzlebox.play.community.shelves

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan

/** What the shelves home shows below its header. Every state but [Loading] carries the [entries] the two entry tiles read. */
internal sealed interface ShelvesUiState {
    data object Loading : ShelvesUiState

    /** The loaded [plan]; [puzzles] holds each card's title, author and level by code and [games] the games it names. */
    data class Content(
        val plan: ShelfPlan,
        val puzzles: Map<ShareCode, ShelfPuzzleModel>,
        val games: Map<GameId, GameChoice>,
        val entries: ShelvesEntries = ShelvesEntries(),
    ) : ShelvesUiState

    /** Nothing is published yet, or all of it is hidden here. */
    data class Empty(val entries: ShelvesEntries = ShelvesEntries()) : ShelvesUiState

    /** The server was not reached; Retry asks again. */
    data class Offline(val entries: ShelvesEntries = ShelvesEntries()) : ShelvesUiState

    /** The server was reached and refused the read; Retry asks again. */
    data class Refused(val entries: ShelvesEntries = ShelvesEntries()) : ShelvesUiState
}

/** This state with [entries] swapped in; [ShelvesUiState.Loading] has none. */
internal fun ShelvesUiState.withEntries(entries: ShelvesEntries): ShelvesUiState = when (this) {
    ShelvesUiState.Loading -> this
    is ShelvesUiState.Content -> copy(entries = entries)
    is ShelvesUiState.Empty -> copy(entries = entries)
    is ShelvesUiState.Offline -> copy(entries = entries)
    is ShelvesUiState.Refused -> copy(entries = entries)
}
