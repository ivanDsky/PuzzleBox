package com.ivandsky.puzzlebox.app.leaderboard

import android.util.Log
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntries
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.model.GameId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The submit hook (brief §3): records writes are the ONLY source of leaderboard
 * entries (A5) — every record the completions store holds is derived into an entry
 * and submitted exactly once per (uid, puzzleRef). Submission is async/eventual with
 * a degrade-to-local posture (A7): signed out or offline, nothing is sent and NO
 * error ever surfaces; the backend's create-if-absent makes the first write stand
 * (A4) and re-submissions after process death are deduped server-side too.
 */
class LeaderboardSubmitWatcher(
    private val records: CompletionRepository,
    private val identityService: IdentityService,
    private val leaderboard: LeaderboardRepository,
    private val calendar: GameCalendar,
    private val scope: CoroutineScope,
) {

    private val submitted = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<Pair<String, String>, Boolean>(),
    )

    fun start() {
        scope.launch {
            combine(records.observeRecords(), identityService.current) { records, identity ->
                records to identity
            }.collect { (records, identity) ->
                val me = identity ?: return@collect // A7: signed out = local-only
                for (record in records) {
                    val entry = LeaderboardEntries.fromRecord(
                        record,
                        uid = me.uid,
                        displayName = me.displayName,
                    ) ?: continue
                    if (!inWindow(entry)) continue
                    val key = me.uid to entry.puzzleRef
                    if (!submitted.add(key)) continue // in-process dedup
                    scope.launch {
                        // A failed attempt un-marks the key: the next records emission
                        // retries it, so an offline flap doesn't silence the player
                        // until process death. A Rejected result stays marked: with the
                        // day window a rejection is permanent.
                        if (!submitQuietly(entry)) submitted.remove(key)
                    }
                }
            }
        }
    }

    /** The entry's day is the game's today or yesterday; an unknown game never submits. */
    private fun inWindow(entry: LeaderboardEntry): Boolean {
        val ref = LeaderboardRef.fromPuzzleRefKey(entry.puzzleRef) ?: return false
        val today = calendar.today(GameId(ref.gameId)) ?: return false
        return ref.day == today || ref.day == today.minusDays(1)
    }

    /** True when the entry is settled (on the board, or permanently rejected); false = retry later. */
    private suspend fun submitQuietly(entry: LeaderboardEntry): Boolean = try {
        when (val result = leaderboard.submit(entry)) {
            is SubmitResult.Ok, is SubmitResult.Deduped -> true
            is SubmitResult.Err -> if (result.error is SubmitError.Rejected) {
                Log.w(TAG, "leaderboard submit rejected: ${result.error}")
                true // permanent: stays marked, never retried
            } else {
                Log.w(TAG, "leaderboard submit deferred: ${result.error}")
                false
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "leaderboard submit deferred: ${e.message}")
        false
    }

    private companion object {
        const val TAG = "LeaderboardSubmit"
    }
}
