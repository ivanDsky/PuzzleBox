package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.mySeat
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import kotlinx.coroutines.flow.first

/**
 * Finds the replay bytes of one side of a duel leg. Answers only MyLeg and TheirLeg; every other ref is null before any read.
 * The replay is advisory (A11): it never checks or changes a result, and a replay that does not fold is simply no replay.
 * A leg's daily puzzle is taken from the cache, else fetched like the play path does, so a leg this device never opened
 * still has its puzzle.
 */
class LoadLegReplay(
    private val matches: MatchRepository,
    private val outbox: MatchResultOutbox,
    private val identity: IdentityService,
    daily: DailyRepository,
    received: ReceivedPuzzleRepository,
    loadShared: LoadSharedPuzzle,
    published: PublishedPuzzleRepository,
) {
    private val puzzles = LoadLegPuzzle(daily, received, loadShared, published)

    /** Null = no replay, for any cause. */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? = (read(ref) as? LegReplayRead.Found)?.source

    /** The same read as [invoke], telling a settled absence from a failed read (offline match, result or puzzle). */
    suspend fun read(ref: SolveReplayRef): LegReplayRead {
        val (code, legIndex) = when (ref) {
            is SolveReplayRef.MyLeg -> ref.code to ref.legIndex
            is SolveReplayRef.TheirLeg -> ref.code to ref.legIndex
            is SolveReplayRef.Record, is SolveReplayRef.Latest, is SolveReplayRef.Player -> return LegReplayRead.Absent
        }
        val match = when (val fetch = matches.observe(code).first()) {
            is MatchFetch.Found -> fetch.match
            MatchFetch.Offline -> return LegReplayRead.Unavailable
            MatchFetch.NotFound, is MatchFetch.UnsupportedVersion -> return LegReplayRead.Absent
        }
        val me = match.mySeat(identity.current.value?.uid) ?: return LegReplayRead.Absent
        val bytes = when (ref) {
            is SolveReplayRef.TheirLeg -> {
                val opponent = match.opponentOf(me) ?: return LegReplayRead.Absent
                when (val read = matches.readResult(match.code, legIndex, opponent.uid)) {
                    is ResultRead.Found -> read.result.replay
                    ResultRead.Offline -> return LegReplayRead.Unavailable
                    ResultRead.Sealed, ResultRead.Missing -> null
                }
            }
            else -> mine(code, legIndex, me)
        } ?: return LegReplayRead.Absent
        return puzzleOf(match, legIndex, bytes)
    }

    private suspend fun mine(code: ShareCode, legIndex: Int, me: String): SolveReplay? =
        outbox.observe(code).first().firstOrNull { it.result.legIndex == legIndex && it.result.uid == me }?.result?.replay

    private suspend fun puzzleOf(match: Match, legIndex: Int, bytes: SolveReplay): LegReplayRead {
        val leg = match.legs.getOrNull(legIndex)?.ref ?: return LegReplayRead.Absent
        return when (val read = puzzles(leg)) {
            is LegPuzzleRead.Found -> LegReplayRead.Found(ReplaySource(read.puzzle, bytes))
            LegPuzzleRead.Unavailable -> LegReplayRead.Unavailable
            LegPuzzleRead.Absent, LegPuzzleRead.TakenDown -> LegReplayRead.Absent
        }
    }
}
