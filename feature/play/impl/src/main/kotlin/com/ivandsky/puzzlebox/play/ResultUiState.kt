package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.resultui.SamePuzzleSolves
import com.ivandsky.puzzlebox.resultui.replay.ReplaySectionState
import java.time.Instant

/**
 * The result route's live facts. [verdict] is the A10 verdict for THIS play — null while
 * the write is in flight/unknown, or when no live play holds this solve; [previousBest]
 * is the game's best before this play, ranked by [rankOrder]; [writeFailed] (the retry
 * affordance) is only ever true for a live play holding this solve.
 *
 * [game] names the game; [puzzleNumber] is the slot's edition number, and [practice] is
 * true once the history shows the slot has none (A22: it never ranks or counts).
 * [doneToday] of [total] is today's playable set solved, this game counted the moment it
 * is solved; [rolloverAt] is when the next puzzles land. [replay] is Ready once this solve is stored with a replay,
 * Hidden before (a pending or failed write), or when there is none (A13). [replayLatest] is false when [replay] read the puzzle's record, so Watch replay opens
 * the record rather than the newest solve. [samePuzzle] holds the player's earlier solves
 * of this puzzle, [SamePuzzleSolves.Unread] until they are read.
 */
data class ResultUiState(
    val verdict: RecordVerdict? = null,
    val previousBest: BestScore? = null,
    val rankOrder: RankOrder = RankOrder.TimeThenHints,
    val writeFailed: Boolean = false,
    val game: String = "",
    val puzzleNumber: Int? = null,
    val practice: Boolean = false,
    val doneToday: Int? = null,
    val total: Int? = null,
    val rolloverAt: Instant? = null,
    val replay: ReplaySectionState = ReplaySectionState.Hidden,
    val replayLatest: Boolean = true,
    val samePuzzle: SamePuzzleSolves = SamePuzzleSolves.Unread,
)

/**
 * This solve's facts while the play holds it; after a play again or another solve, the
 * verdict this entry already saw stays (it was this solve's), and retry hides.
 */
internal fun ResultUiState.withRecord(record: ActivePlay.Record, solve: ActivePlay.Solve): ResultUiState =
    if (record.solve == solve) {
        copy(
            verdict = record.verdict,
            previousBest = record.previousBest,
            rankOrder = record.rankOrder,
            writeFailed = record.writeFailed,
        )
    } else {
        copy(writeFailed = false)
    }
