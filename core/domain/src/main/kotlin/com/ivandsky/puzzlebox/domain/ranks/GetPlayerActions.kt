package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.visibility.TierLookup
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Challenge
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Watch
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.flow.first

/**
 * Tier, the viewer's own solve and the replay flag → what they may do with [uid]: one tier read per call. A tier that
 * cannot be read fails closed (everything Private). Challenge needs the player's tier at Duel or above; Watch needs
 * Replays, the viewer's own solve of that same game and day (the spoiler gate, A21) and replays switched on.
 */
class GetPlayerActions(
    private val visibility: VisibilityRepository,
    private val completions: CompletionRepository,
    private val flags: ReleaseFlags,
) {
    /** [board] is the one puzzle the row is about; null when the board has none (Overall, a week or a month): nothing to watch. */
    suspend operator fun invoke(uid: String, board: LeaderboardRef?): PlayerActions {
        val tier = (visibility.tierOf(uid) as? TierLookup.Found)?.tier ?: return PlayerActions(Challenge.Private, Watch.Private)
        val challenge = if (tier.allowsChallenge) Challenge.Allowed else Challenge.Private
        val watch = when {
            board == null || !flags.isEnabled(ReleaseFlag.FullReplay) -> Watch.Unavailable
            !tier.allowsReplays -> Watch.Private
            !viewerSolved(board) -> Watch.SolveFirst
            else -> Watch.Allowed
        }
        return PlayerActions(challenge, watch)
    }

    private suspend fun viewerSolved(board: LeaderboardRef): Boolean = completions.observeRecords().first().any {
        val key = it.puzzleRef.key
        it.source == CompletionSource.App && it.puzzleRef.gameId.value == board.gameId && key is PuzzleKey.Daily && key.date == board.day
    }
}
