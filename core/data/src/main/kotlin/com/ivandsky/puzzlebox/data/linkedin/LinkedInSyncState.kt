package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/**
 * One game day of one linked profile: the unit a sync covers and is deduplicated by. The profile is part
 * of the key so that what one account's sync left behind can never be read, awaited or shared by another.
 */
internal data class DayKey(val profileId: String, val game: GameId, val day: LocalDate)

/**
 * What the day syncs remember for the life of the process and never write to disk: which days are
 * being synced right now, and how the last sync of each day ended.
 */
internal class LinkedInSyncState {

    /** The running sync of each day. Its owner completes the deferred; everyone else awaits it. */
    private val claims = MutableStateFlow<Map<DayKey, CompletableDeferred<LinkedInSync>>>(emptyMap())

    private val results = MutableStateFlow<Map<DayKey, LinkedInSync>>(emptyMap())

    fun inFlight(key: DayKey): Flow<Boolean> = claims.map { key in it }.distinctUntilChanged()

    /** How the last sync of [key] in this process ended, or null before the first. */
    fun lastSync(key: DayKey): Flow<LinkedInSync?> = results.map { it[key] }.distinctUntilChanged()

    /**
     * Runs [sync] unless one is already running for [key]; then waits for that one's result instead, so
     * a day viewed from two places at once is asked of LinkedIn once.
     */
    suspend fun shared(key: DayKey, sync: suspend () -> LinkedInSync): LinkedInSync {
        while (true) {
            val mine = CompletableDeferred<LinkedInSync>()
            val running = claims.updateAndGet { if (key in it) it else it + (key to mine) }.getValue(key)
            if (running === mine) return owned(key, mine, sync)
            try {
                return running.await()
            } catch (e: CancellationException) {
                // Thrown when this caller was cancelled and also when the owner was. Only the first ends
                // this caller; after the second it runs the day itself, which reads the fetch log again,
                // so an item the cancelled run already logged is not asked twice.
                currentCoroutineContext().ensureActive()
            }
        }
    }

    /**
     * Forgets everything, so a disconnected session does not hold memory for the rest of the process.
     * Not what keeps one account's results from another: the keys carry the profile for that.
     */
    fun clear() {
        claims.value = emptyMap()
        results.value = emptyMap()
    }

    private suspend fun owned(
        key: DayKey,
        mine: CompletableDeferred<LinkedInSync>,
        sync: suspend () -> LinkedInSync,
    ): LinkedInSync {
        val result = try {
            sync()
        } catch (e: Throwable) {
            release(key, mine)
            // A cancelled owner hands the day to whoever waits. Any other failure is the waiters' too:
            // the call may already have been sent, and running the day again would send it twice.
            if (e is CancellationException) mine.cancel(e) else mine.completeExceptionally(e)
            throw e
        }
        // Paused and not-linked are never kept: the persisted link state is their only source, so the
        // two cannot disagree.
        if (result is LinkedInSync.Done || result is LinkedInSync.Offline) {
            results.update { it + (key to result) }
        }
        release(key, mine)
        mine.complete(result)
        return result
    }

    /**
     * Frees [key] before its waiters wake, so one that takes over finds it free. Only while it is still
     * this owner's: a [clear] and a newer claim may have replaced it.
     */
    private fun release(key: DayKey, mine: CompletableDeferred<LinkedInSync>) {
        claims.update { if (it[key] === mine) it - key else it }
    }
}
