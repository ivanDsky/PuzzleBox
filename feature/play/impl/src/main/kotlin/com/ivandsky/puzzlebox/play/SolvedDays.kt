package com.ivandsky.puzzlebox.play

import android.util.Log
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Which days are solved, for [PlaySessionStore]: each day's first solve as the record rows
 * hold it (a live snapshot, replaced on every emission) plus the solves of released plays
 * until their record lands. Confined to the store's main-bound [scope], like the store.
 */
internal class SolvedDays(
    private val completions: CompletionRepository,
    scope: CoroutineScope,
) {
    private var recordedSolves = emptyMap<PlaySlot.Daily, ActivePlay.Solve>()

    /** The records flow errored; [isCold] stays true until a one-shot read lands. */
    private var snapshotFailed = false

    /** Solves of released plays until their record lands in [recordedSolves]. */
    private val releasedSolves = HashMap<PlaySlot.Daily, ActivePlay.Solve>()

    /** Completes when [recordedSolves] holds the first records read. */
    private val snapshotLoaded = CompletableDeferred<Unit>()

    /** True until the first records read lands, and while the snapshot is failed. */
    val isCold: Boolean get() = !snapshotLoaded.isCompleted || snapshotFailed

    init {
        scope.launch {
            try {
                completions.observeRecords().collect(::adopt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "completed-day snapshot stopped: ${e.message}")
                snapshotFailed = true
                snapshotLoaded.complete(Unit)
            }
        }
    }

    /** [slot]'s first solve, as the records or a released play hold it; null when unsolved. */
    fun solvedBefore(slot: PlaySlot.Daily): ActivePlay.Solve? = recordedSolves[slot] ?: releasedSolves[slot]

    /** Remembers a released play's [solve] of [slot] until its record lands. */
    fun release(slot: PlaySlot.Daily, solve: ActivePlay.Solve) {
        releasedSolves[slot] = solve
    }

    /** Suspends until the first records read lands; a failed snapshot gets one one-shot read. */
    suspend fun awaitLoaded() {
        snapshotLoaded.await()
        if (snapshotFailed) readOnce()
    }

    private fun adopt(rows: List<CompletionRecord>) {
        recordedSolves = rows.mapNotNull { row ->
            val date = (row.puzzleRef.key as? PuzzleKey.Daily)?.date ?: return@mapNotNull null
            PlaySlot.Daily(row.puzzleRef.gameId, date) to ActivePlay.Solve(row.time, row.hints)
        }.toMap()
        releasedSolves.keys.removeAll(recordedSolves.keys)
        snapshotFailed = false
        snapshotLoaded.complete(Unit)
    }

    /** The records read once, for when the live snapshot failed; a failing read leaves it failed. */
    private suspend fun readOnce() {
        try {
            adopt(completions.observeRecords().first())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "completed-day read failed: ${e.message}")
        }
    }
}

private const val TAG = "PlaySessionStore"
