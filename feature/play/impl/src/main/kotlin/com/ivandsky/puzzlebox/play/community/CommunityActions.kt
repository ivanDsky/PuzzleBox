package com.ivandsky.puzzlebox.play.community

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/** What the New today section can ask for: a row opened or reported, a filter picked or cleared, a retry. */
internal class CommunityActions(
    val onOpen: (ShareCode) -> Unit,
    val onReport: (ShareCode) -> Unit,
    val onGame: (GameId?) -> Unit,
    val onDifficulty: (DifficultyChoice?) -> Unit,
    val onClearFilters: () -> Unit,
    val onRetry: () -> Unit,
)
