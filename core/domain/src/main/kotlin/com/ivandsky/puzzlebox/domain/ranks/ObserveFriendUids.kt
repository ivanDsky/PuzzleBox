package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The viewer's friends: everyone they share a duel with. Puzzle authors are not friends. Empty while
 * signed out or before any opponent has claimed a seat.
 */
class ObserveFriendUids(
    private val matches: MatchRepository,
    private val identity: IdentityService,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Set<String>> =
        identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { me ->
            if (me == null) {
                flowOf(emptySet())
            } else {
                matches.observeMine().map { mine ->
                    mine.orEmpty().mapNotNullTo(HashSet()) { it.opponentOf(me)?.uid?.takeIf { uid -> uid != me } }
                }
            }
        }.distinctUntilChanged()
}
