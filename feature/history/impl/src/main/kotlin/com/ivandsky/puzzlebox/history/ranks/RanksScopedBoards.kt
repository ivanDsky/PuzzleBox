package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

/**
 * The window's boards with who each scope ranks on them, both off the hub's one read ([global]): Global ranks everyone
 * on that read, Friends the viewer and their friends, so every place, N and point drawn for Friends counts friends only.
 * A scope change therefore reads no board. The boards are narrowed where connections are counted ([BoardGhosts.counted]),
 * so both steps run in the domain's one Friends pipeline. The friends are listened to only while something shows Friends.
 */
internal class RanksScopedBoards(
    global: StateFlow<Pair<RankWindow, RankWindowBoards>?>,
    observeFriendUids: ObserveFriendUids,
    viewer: Flow<Identity?>,
    scope: CoroutineScope,
) {
    /** The window's [boards] as read, with the players a scope ranks [among] on them; null ranks everyone. */
    data class Scoped(val boards: RankWindowBoards, val among: Set<String>?)

    private val everyone: Flow<Pair<RankWindow, Scoped>?> = global.map { it?.let { (window, boards) -> window to Scoped(boards, among = null) } }

    // Null until the friends answer: the boards wait as Loading rather than show Global's places as friends'.
    private val friends: Flow<Pair<RankWindow, Scoped>?> = combine(global, observeFriendUids(), viewer) { answered, friends, me ->
        answered?.let { (window, boards) -> window to Scoped(boards, among = friends + setOfNotNull(me?.uid)) }
    }.onStart { emit(null) }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS, replayExpirationMillis = 0), replay = 1)

    /** The boards [scope] ranks, tagged with the window they answer; null while there is no answer yet. */
    fun of(scope: RankScope): Flow<Pair<RankWindow, Scoped>?> = if (scope == RankScope.Global) everyone else friends

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
