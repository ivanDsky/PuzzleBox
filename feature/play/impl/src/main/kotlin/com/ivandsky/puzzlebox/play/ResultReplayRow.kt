package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.SolveReplayRef
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.resultui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.resultui.replay.ReplayUnavailable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.runningFold

/**
 * What the Watch replay row shows and which stored solve it opens. [latest] is false when the row read the puzzle's
 * record, true for the newest solve; the route's open key follows it, so the page loads the replay the row showed.
 */
internal data class ResultReplay(val section: ReplaySectionState, val latest: Boolean = true)

/**
 * Result's Watch replay row for one [solve]. The replay is read from the stored completion, never from the live play:
 * the row shows once the solve is stored and never while the write is pending or failed. [live] is the slot's live
 * play only when it held this solve as Result opened; null otherwise (as after process death).
 */
internal class ResultReplayRow(
    private val completions: CompletionRepository,
    private val loadReplay: LoadSolveReplay,
    private val solve: ActivePlay.Solve,
    private val live: ActivePlay?,
) {
    /** The row once [storedOf] says this solve is stored; [loadReplay] answers null for a solve with no replay. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun of(ref: PuzzleRef): Flow<ResultReplay> =
        storedOf(ref)
            .distinctUntilChanged()
            .mapLatest { stored ->
                if (!stored) {
                    ResultReplay(ReplaySectionState.Hidden)
                } else {
                    val replayRef = replayRefOf(ref)
                    val section = loadReplay(replayRef)?.let(ReplaySectionState::Ready) ?: unavailableOf(ref)
                    ResultReplay(section, latest = replayRef !is SolveReplayRef.Record)
                }
            }

    /**
     * True once this solve is stored. A live play that holds the solve says so itself ([ActivePlay.Record.stored]);
     * the time and hints match against the stored rows is only the cold case (no live play, after process death), where
     * no write signal exists. It must not decide the live case: an older row with the same time and hints would show
     * the row before the new write lands.
     */
    private fun storedOf(ref: PuzzleRef): Flow<Boolean> =
        if (live != null) {
            live.record.map { it.solve == solve && it.stored }.runningFold(false) { seen, now -> seen || now }
        } else {
            combine(completions.observeRecords(), completions.observePlayAgains(ref)) { records, again ->
                (records + again).any { it.puzzleRef == ref && it.time == solve.timeMs && it.hints == solve.hints }
            }
        }

    /**
     * The replay of the stored row that is this solve. A cold result whose time and hints match the puzzle's record row
     * reads the record's replay, so a newer import or practice row never hides it. A live solve was just written, so it
     * is the newest row.
     */
    private suspend fun replayRefOf(ref: PuzzleRef): SolveReplayRef {
        val isRecord = live == null &&
            completions.observeRecords().first().any { it.puzzleRef == ref && it.time == solve.timeMs && it.hints == solve.hints }
        return if (isRecord) SolveReplayRef.Record(ref) else SolveReplayRef.Latest(ref)
    }

    /** A stored solve with no replay says so, naming a LinkedIn import. */
    private suspend fun unavailableOf(ref: PuzzleRef): ReplaySectionState {
        val stored = completions.observeRecords().first() + completions.observePlayAgains(ref).first()
        val source = stored.firstOrNull { it.puzzleRef == ref && it.time == solve.timeMs && it.hints == solve.hints }?.source
        return ReplaySectionState.Unavailable(source?.let(ReplayUnavailable::of) ?: ReplayUnavailable.NotRecorded)
    }
}
