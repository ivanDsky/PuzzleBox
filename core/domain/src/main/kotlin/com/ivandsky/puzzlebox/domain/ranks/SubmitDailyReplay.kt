package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.leaderboard.inSubmitWindow
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.replay.DailyReplayRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Publishes the record solve's replay of a daily board once its post is accepted, if the player is on the Replays tier
 * at that moment (A20). Each board is decided once per run: a solve accepted on a lower tier stays private after a
 * later opt-in, and a failed publish waits for the next app start. Only boards in the submit window are followed,
 * because no other post can be accepted and the backend refuses older replays. Runs until cancelled.
 */
class SubmitDailyReplay(
    private val completions: CompletionRepository,
    private val identity: IdentityService,
    private val calendar: GameCalendar,
    private val statuses: SubmitStatusTracker,
    private val visibility: VisibilityRepository,
    private val replays: DailyReplayRepository,
) {
    suspend operator fun invoke(): Unit = coroutineScope {
        val followed = HashSet<Pair<String, LeaderboardRef>>()
        combine(completions.observeRecords(), identity.current) { records, me -> records to me }
            .collect { (records, me) ->
                val uid = me?.uid ?: return@collect
                for (record in records) {
                    val board = LeaderboardRef.fromPuzzleRefKey(PuzzleRefKeys.encode(record.puzzleRef)) ?: continue
                    if (!calendar.inSubmitWindow(GameId(board.gameId), board.day)) continue
                    if (followed.add(uid to board)) launch { publishOnceAccepted(uid, board, record.puzzleRef) }
                }
            }
    }

    private suspend fun publishOnceAccepted(uid: String, board: LeaderboardRef, puzzle: PuzzleRef) {
        statuses.observe(uid, board).first { it == SubmitStatus.Posted }
        // The repository publishes as whoever is signed in now, which must still be the player whose post was accepted.
        if (identity.current.value?.uid != uid) return
        // Waits for a loaded tier so an unknown one never publishes.
        val tier = visibility.ownTier.first { it != OwnTier.Unknown }.effective
        if (tier?.allowsReplays != true) return
        val replay = completions.recordReplay(puzzle) ?: return
        replays.publish(board, replay.toByteArray())
    }
}
