package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.network.NetworkStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest

/**
 * When the hub's window asks [ObserveRankWindow] to read what failed again (A24): at once on a
 * manual pull and on the false-to-true edge of [NetworkStatus.online], and, while any board is
 * Unavailable and the device is online, on one exponential backoff for the whole window (not one
 * per board). A pull or a reconnect restarts that schedule; going offline pauses it.
 *
 * Open boards recover through Firestore's own listeners; a tick only re-reads Final boards and
 * re-counts capped open ones, so a window of Loaded boards costs nothing per pull.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWindowRefresh(
    private val network: NetworkStatus,
    private val firstDelay: Duration = 2.seconds,
    private val maxDelay: Duration = 5.minutes,
) {

    /** [pulls] are the manual refreshes; [boards] is what the window currently shows, so the backoff stops once nothing is Unavailable. */
    operator fun invoke(pulls: Flow<Unit>, boards: Flow<RankWindowBoards>): Flow<Unit> = flow {
        val failing = boards.map { it is RankWindowBoards.Boards && it.boards.any { board -> board is BoardLoad.Unavailable } }.distinctUntilChanged()
        // One collection of the connection: a pull and an edge are both just a new epoch of the same state.
        val links = merge(pulls.map { null }, network.online.distinctUntilChanged())
            .runningFold(Link(online = null, epoch = 0)) { link, change ->
                when {
                    change == null -> link.copy(epoch = link.epoch + 1)
                    change && link.online == false -> Link(online = true, epoch = link.epoch + 1)
                    else -> link.copy(online = change)
                }
            }
        var served = 0
        emitAll(
            combine(links, failing, ::Pair).transformLatest { (link, failing) ->
                if (link.epoch != served) {
                    emit(Unit)
                    served = link.epoch
                }
                if (failing && link.online != false) {
                    var wait = firstDelay
                    while (true) {
                        delay(wait)
                        emit(Unit)
                        wait = (wait * 2).coerceAtMost(maxDelay)
                    }
                }
            },
        )
    }

    private data class Link(val online: Boolean?, val epoch: Int)
}
