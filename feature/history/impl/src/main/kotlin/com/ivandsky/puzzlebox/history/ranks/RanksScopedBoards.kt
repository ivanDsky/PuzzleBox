package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.CountedBoards
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendBoards
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

/**
 * The window's boards as each scope ranks them, both off the hub's one read ([global]): Global ranks everyone on that
 * read, Friends the viewer and their friends with their LinkedIn connections counted ([friendBoards], the domain's one
 * Friends pipeline, the one Friends Overall runs), so every place, N and point drawn for Friends counts friends only. A
 * scope change therefore reads no board. The friends are listened to only while something shows Friends.
 */
internal class RanksScopedBoards(
    private val global: StateFlow<Pair<RankWindow, RankWindowBoards>?>,
    private val friendBoards: ObserveFriendBoards,
    scope: CoroutineScope,
) {
    // Null until the friends answer: the boards wait as Loading rather than show Global's places as friends'.
    private val friends: Flow<Pair<RankWindow, ObserveFriendBoards.Among>?> = friendBoards.among(global)
        .onStart { emit(null) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS, replayExpirationMillis = 0), replay = 1)

    /** The boards [scope] ranks in [window], counted; null until that window answers in that scope. */
    fun of(scope: RankScope, window: RankWindow): Flow<CountedBoards?> = if (scope == RankScope.Global) {
        global.answering(window).map { it?.let { boards -> CountedBoards(boards, boards, emptyMap()) } }
    } else {
        friendBoards(friends.answering(window))
    }

    /** [window]'s answer, null while there is none or the answer is another window's. */
    private fun <T> Flow<Pair<RankWindow, T>?>.answering(window: RankWindow): Flow<T?> = map { it?.takeIf { (of, _) -> of == window }?.second }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
