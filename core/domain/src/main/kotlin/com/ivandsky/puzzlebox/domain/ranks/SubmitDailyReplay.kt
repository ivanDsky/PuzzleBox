package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.leaderboard.inSubmitWindow
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.replay.DailyReplayRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.visibility.ReplayOptInStamp
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Publishes the record solve's replay of a daily board once its post is accepted, if the player is on the Replays tier
 * at that moment and opted in before the solve (A20: the opt-in covers future solves only). The opt-in is the instant
 * this device first saw the tier at Replays, kept in [optIns] and dropped when the tier is seen below Replays; a solve
 * completed before it never publishes, which also holds after a restart re-posts an old solve. No stamp, or a solve
 * without a completion time, fails closed. This use case is the only writer of the stamp, from the same tier flow the
 * publish decision reads, so a decision never runs ahead of its stamp. A stamp write that fails leaves the tier unknown
 * until a later write succeeds, so nothing is decided on a stamp that was not written or not cleared. Each board is
 * decided once per run, and a failed publish waits for the next app start. Only boards in the submit window are
 * followed, because no other post can be accepted and the backend refuses older replays. Runs until cancelled.
 */
class SubmitDailyReplay(
    private val completions: CompletionRepository,
    private val identity: IdentityService,
    private val calendar: GameCalendar,
    private val statuses: SubmitStatusTracker,
    private val visibility: VisibilityRepository,
    private val optIns: ReplayOptInStamp,
    private val replays: DailyReplayRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val failures: FailureLog = LogcatFailureLog,
) {
    /** A loaded tier of the signed-in player, with its stamp already written. */
    private data class StampedTier(val uid: String, val tier: VisibilityTier)

    suspend operator fun invoke(): Unit = coroutineScope {
        val tiers = stampTiers(this)
        val followed = HashSet<Pair<String, LeaderboardRef>>()
        combine(completions.observeRecords(), identity.current) { records, me -> records to me }
            .collect { (records, me) ->
                val uid = me?.uid ?: return@collect
                for (record in records) {
                    val board = LeaderboardRef.fromPuzzleRefKey(PuzzleRefKeys.encode(record.puzzleRef)) ?: continue
                    if (!calendar.inSubmitWindow(GameId(board.gameId), board.day)) continue
                    if (followed.add(uid to board)) launch { publishOnceAccepted(uid, board, record, tiers) }
                }
            }
    }

    /**
     * Null while signed out, while the tier is unknown, or while its stamp could not be written, so nothing reads an
     * unloaded tier as a choice. The tier is tried again only when it or the player changes.
     */
    private fun stampTiers(scope: CoroutineScope): StateFlow<StampedTier?> =
        combine(identity.current.map { it?.uid }.distinctUntilChanged(), visibility.ownTier) { uid, own -> uid to own.effective }
            .distinctUntilChanged()
            .map { (uid, tier) -> if (uid != null && tier != null && stamp(uid, tier)) StampedTier(uid, tier) else null }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** False when the store failed. Thrown from the shared tier flow, the failure would end [invoke] and its caller. */
    private suspend fun stamp(uid: String, tier: VisibilityTier): Boolean = try {
        if (tier.allowsReplays) optIns.markIfAbsent(uid, clock.millis()) else optIns.clear(uid)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failures.warn(TAG, "replay opt-in stamp write failed", e)
        false
    }

    private suspend fun publishOnceAccepted(
        uid: String,
        board: LeaderboardRef,
        record: CompletionRecord,
        tiers: StateFlow<StampedTier?>,
    ) {
        statuses.observe(uid, board).first { it == SubmitStatus.Posted }
        // The repository publishes as whoever is signed in now, which must still be the player whose post was accepted.
        if (identity.current.value?.uid != uid) return
        // Waits for a loaded tier so an unknown one never publishes.
        if (!tiers.filterNotNull().first { it.uid == uid }.tier.allowsReplays) return
        val optedInAt = optIns.since(uid) ?: return
        val completedAt = record.firstCompletedAt?.toEpochMilli() ?: return
        if (completedAt < optedInAt) return
        val replay = completions.recordReplay(record.puzzleRef) ?: return
        replays.publish(board, replay.toByteArray())
    }

    private companion object {
        const val TAG = "SubmitDailyReplay"
    }
}
