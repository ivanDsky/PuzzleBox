package com.ivandsky.puzzlebox.data.match

import android.util.Log
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.network.NetworkStatus
import com.ivandsky.puzzlebox.model.ShareCode
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Posts the signed-in user's pending duel results from the outbox and records each outcome on its row. Rows of
 * another account wait until that account signs in. Lateness is the repository's verdict (server clock), never the
 * device's.
 *
 * Each row is tried at most once per process: a rejection waits for the next app start or [poke], so [MAX_ATTEMPTS]
 * spans app starts rather than one burst. An unavailable repository or a failed call leaves the row Pending and
 * untried, so the next change of identity or rows, or the network coming back, tries again. A row is never posted
 * twice at once: [poke] forgets only the rows whose post has finished, so a post still in flight is its own retry.
 */
class MatchResultPoster(
    private val identity: IdentityService,
    private val outbox: MatchResultOutbox,
    private val matches: MatchRepository,
    private val network: NetworkStatus,
    private val scope: CoroutineScope,
) : MatchResultSync {

    private val tried: MutableSet<RowKey> = ConcurrentHashMap.newKeySet()
    private val inFlight: MutableSet<RowKey> = ConcurrentHashMap.newKeySet()
    private val pokes = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            combine(identity.current, pokes) { me, _ -> me }.flatMapLatest { me ->
                if (me == null) {
                    flowOf(emptyList())
                } else {
                    outbox.observePending().map { rows -> rows.filter { it.result.uid == me.uid } }
                }
            }.collect { rows ->
                for (row in rows) {
                    val key = RowKey(row.code, row.result.legIndex, row.result.uid)
                    if (key in inFlight || key in tried) continue
                    // In flight first, so a concurrent poke never drops a key that is still being posted.
                    inFlight += key
                    tried += key
                    scope.launch {
                        try {
                            if (!post(row)) tried.remove(key)
                        } finally {
                            inFlight.remove(key)
                        }
                    }
                }
            }
        }
        // A regained connection retries what is still Pending; the first value is the state at start, not an edge.
        scope.launch {
            network.online.distinctUntilChanged().drop(1).filter { it }.collect { poke() }
        }
    }

    override fun poke() {
        tried.retainAll(inFlight)
        pokes.update { it + 1 }
    }

    /** True when the outcome is settled on the row; false leaves the row to retry on a later change. */
    private suspend fun post(row: OutboxResult): Boolean = try {
        var outcome = matches.post(row.code, row.result)
        // A replay must never cost the result: a rejection of a result carrying one is answered by the same result bare.
        if (outcome is PostResult.Rejected && row.result.replay != null) {
            Log.w(TAG, "duel result with replay rejected, retrying without it: ${outcome.message}")
            outcome = matches.post(row.code, row.result.copy(replay = null))
        }
        when (outcome) {
            PostResult.Posted, PostResult.Duplicate -> settle(row, ResultPostState.Posted, row.attempts)
            PostResult.Late -> settle(row, ResultPostState.Late, row.attempts)
            is PostResult.Rejected -> {
                Log.w(TAG, "duel result rejected: ${outcome.message}")
                val attempts = row.attempts + 1
                settle(row, if (attempts >= MAX_ATTEMPTS) ResultPostState.LocalOnly else ResultPostState.Pending, attempts)
            }
            is PostResult.Unavailable -> {
                Log.w(TAG, "duel result post deferred: ${outcome.message}")
                false
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "duel result post deferred: ${e.message}")
        false
    }

    private suspend fun settle(row: OutboxResult, state: ResultPostState, attempts: Int): Boolean {
        outbox.update(row.code, row.result.legIndex, row.result.uid, state, attempts)
        return true
    }

    private data class RowKey(val code: ShareCode, val legIndex: Int, val uid: String)

    companion object {
        const val MAX_ATTEMPTS: Int = 3
        private const val TAG = "MatchResultPoster"
    }
}
