package com.ivandsky.puzzlebox.data.leaderboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The daily submit watcher's outcome per (uid, puzzleRef), the same key as its dedup set,
 * for the screens that would otherwise show Posting until the player's own row lands.
 * One app-scoped instance: the watcher writes, readers observe. In memory only; after
 * process death every post reads [SubmitStatus.Posting] until the watcher tries it again.
 */
class SubmitStatusTracker {

    private val statuses = MutableStateFlow<Map<Pair<String, String>, SubmitStatus>>(emptyMap())

    /** Records [status] for [uid]'s entry on [puzzleRef] (a `gameId/<ISO-day>/<n>` key). */
    fun set(uid: String, puzzleRef: String, status: SubmitStatus) {
        statuses.update { it + ((uid to puzzleRef) to status) }
    }

    /**
     * [uid]'s post to [board]: a daily board holds exactly one puzzle, so the board names the
     * puzzleRef. [SubmitStatus.Posting] until the watcher has written anything for it.
     */
    fun observe(uid: String, board: LeaderboardRef): Flow<SubmitStatus> = statuses
        .map { all ->
            all.entries.firstOrNull { (key, _) -> key.first == uid && LeaderboardRef.fromPuzzleRefKey(key.second) == board }
                ?.value ?: SubmitStatus.Posting
        }
        .distinctUntilChanged()
}
