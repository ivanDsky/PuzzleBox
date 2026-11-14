package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.amongFriends
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

/**
 * The window's boards as each scope ranks them (F18), both off the hub's one read ([global], A23): Global is that
 * read as it is, Friends is the same boards narrowed to the viewer and their friends ([amongFriends]), so every
 * place, N and point drawn from them counts friends only. A scope change therefore reads no board. The friends are
 * listened to only while something shows Friends, and once for all of it.
 */
internal class RanksScopedBoards(
    private val global: StateFlow<Pair<RankWindow, RankWindowBoards>?>,
    observeFriendUids: ObserveFriendUids,
    viewer: Flow<Identity?>,
    scope: CoroutineScope,
) {
    // Null until the friends answer: the boards wait as Loading rather than show Global's places as friends'.
    private val friends: Flow<Pair<RankWindow, RankWindowBoards>?> = combine(global, observeFriendUids(), viewer) { answered, friends, me ->
        answered?.let { (window, boards) -> window to boards.amongFriends(friends + setOfNotNull(me?.uid)) }
    }.onStart { emit(null) }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS, replayExpirationMillis = 0), replay = 1)

    /** The boards [scope] ranks, tagged with the window they answer; null while there is no answer yet. */
    fun of(scope: RankScope): Flow<Pair<RankWindow, RankWindowBoards>?> = if (scope == RankScope.Global) global else friends

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
