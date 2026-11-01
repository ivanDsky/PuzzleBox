package com.ivandsky.puzzlebox.app.leaderboard

import android.util.Log
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntries
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
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
                    val key = me.uid to entry.puzzleRef
                    if (!submitted.add(key)) continue // in-process dedup
                    scope.launch {
                        // A failed attempt un-marks the key: the next records emission
                        // retries it, so an offline flap doesn't silence the player
                        // until process death. Permission rejections retry too, but
                        // the rules-backed first-write-wins keeps that harmless.
                        if (!submitQuietly(entry)) submitted.remove(key)
                    }
                }
            }
        }
    }

    /** True when the entry is durably on the board (new or deduped); false = retry later. */
    private suspend fun submitQuietly(entry: LeaderboardEntry): Boolean = try {
        when (val result = leaderboard.submit(entry)) {
            is SubmitResult.Ok, is SubmitResult.Deduped -> true
            is SubmitResult.Err -> {
                // A7: offline/failed submissions never surface; the local record already
                // stands and the watcher retries on the next records emission.
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
