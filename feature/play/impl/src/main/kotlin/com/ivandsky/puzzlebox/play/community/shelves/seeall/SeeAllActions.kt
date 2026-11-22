package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.DifficultyChoice

/** What a See all page can ask for: leaving it, changing the query, a filter or the order, and acting on a row. */
internal class SeeAllActions(
    val onBack: () -> Unit,
    val onQuery: (String) -> Unit,
    val onGame: (GameId?) -> Unit,
    val onDifficulty: (DifficultyChoice?) -> Unit,
    val onSort: (SeeAllSort) -> Unit,
    val onClearFilters: () -> Unit,
    val onOpen: (ShareCode) -> Unit,
    val onReport: (ShareCode) -> Unit,
    val onRetry: () -> Unit,
)
