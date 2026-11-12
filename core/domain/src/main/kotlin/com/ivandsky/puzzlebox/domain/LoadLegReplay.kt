package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Finds the replay bytes of one side of a duel leg. Answers only MyLeg and TheirLeg; every other ref is null before any read.
 * The replay is advisory (A11): it never checks or changes a result, and a replay that does not fold is simply no replay.
 */
class LoadLegReplay(
    private val matches: MatchRepository,
    private val outbox: MatchResultOutbox,
    private val identity: IdentityService,
    private val daily: DailyRepository,
    private val received: ReceivedPuzzleRepository,
    private val loadShared: LoadSharedPuzzle,
) {
    /** Null = no replay, for any cause. Cancellation still propagates. */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? {
        val (code, legIndex) = when (ref) {
            is SolveReplayRef.MyLeg -> ref.code to ref.legIndex
            is SolveReplayRef.TheirLeg -> ref.code to ref.legIndex
            is SolveReplayRef.Record, is SolveReplayRef.Latest -> return null
        }
        return try {
            val me = identity.current.value?.uid ?: return null
            val match = (matches.observe(code).first() as? MatchFetch.Found)?.match ?: return null
            val bytes = when (ref) {
                is SolveReplayRef.TheirLeg -> theirs(match, legIndex, me)
                else -> mine(code, legIndex, me)
            } ?: return null
            val puzzle = puzzleOf(match, legIndex) ?: return null
            ReplaySource(puzzle, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun mine(code: ShareCode, legIndex: Int, me: String): SolveReplay? =
        outbox.observe(code).first().firstOrNull { it.result.legIndex == legIndex && it.result.uid == me }?.result?.replay

    private suspend fun theirs(match: Match, legIndex: Int, me: String): SolveReplay? {
        val opponent = match.opponentOf(me) ?: return null
        return (matches.readResult(match.code, legIndex, opponent.uid) as? ResultRead.Found)?.result?.replay
    }

    private suspend fun puzzleOf(match: Match, legIndex: Int): Puzzle? {
        val leg = match.legs.getOrNull(legIndex)?.ref ?: return null
        return when (leg) {
            is LegRef.Daily -> daily.cachedPuzzle(leg.gameId, leg.day)
            is LegRef.Custom -> {
                val row = received.get(leg.code) ?: (loadShared(leg.code) as? OpenOutcome.Opened)?.puzzle
                row?.takeIf { it.gameId == leg.gameId }
                    ?.let { Puzzle(it.gameId, it.variant, it.sharedFormatVersion, it.puzzleBytes) }
            }
        }
    }
}
