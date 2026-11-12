package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The record of one play's win: the write (fire-and-observe, on [recordScope] so a release
 * never cancels it), its retry, and the verdict against the session-start baseline.
 */
internal class WinRecording(
    private val plugin: GamePlugin<*, *, *>,
    private val completions: CompletionRepository,
    private val scope: CoroutineScope,
    private val recordScope: CoroutineScope,
) {
    /** The record write's identity: bumped by [reset] only — never by a controller swap. */
    private var recordToken = 0

    private val _record = MutableStateFlow(blankRecord(labelFor(null)))
    val record: StateFlow<ActivePlay.Record> = _record.asStateFlow()

    /** The game's best BEFORE this session and whether this puzzle was already
     *  solved (a replay), read at session start: the verdict is derived from this
     *  baseline, never from the stored rows after the write. [baselineKnown] false = the
     *  read has not landed (the verdict stays null). */
    private var baselineKnown = false
    private var baselineBest: BestScore? = null
    private var baselinePlayAgain = false
    private var lastWon: CompletionRecord? = null

    fun reset(ref: PuzzleRef?) {
        recordToken++
        baselineKnown = false
        baselineBest = null
        baselinePlayAgain = false
        lastWon = null
        _record.value = blankRecord(labelFor(ref))
    }

    /** [playAgain] true = known without a read (Play again: the solve's write may still be in
     *  flight). Otherwise a record row with this ref's identity (ADR-0004) makes it one. */
    fun refreshBaseline(ref: PuzzleRef?, playAgain: Boolean = false) {
        if (ref == null) return
        val token = recordToken
        scope.launch {
            val best = completions.best(ref.gameId)
            val solved = playAgain || completions.observeRecords().first().any { it.puzzleRef.sameIdentity(ref) }
            if (token == recordToken) {
                baselineBest = best
                baselinePlayAgain = solved
                baselineKnown = true
            }
        }
    }

    fun onWon(record: CompletionRecord) {
        lastWon = record
        _record.update { it.copy(solve = ActivePlay.Solve(record.time, record.hints)) }
        emit(record)
    }

    /** Retry after a failed record write: a new emitter (the old one deduped this ref). */
    fun retry() {
        emit(lastWon ?: return)
    }

    private fun emit(record: CompletionRecord) {
        val token = recordToken
        _record.update { it.copy(verdict = null, previousBest = null, writeFailed = false) }
        CompletionRecordEmitter(recordScope) { r ->
            completions.onCompleted(r)
            AppResult.Ok(Unit)
        }.emit(record) { result ->
            if (token != recordToken) return@emit
            when (result) {
                // Verdict for THIS play against the session-start best; an unknown
                // baseline (the write raced the baseline read) stays null. A practice
                // puzzle (no edition number) never ranks: its verdict stays null.
                is AppResult.Ok -> _record.update {
                    if (!baselineKnown || record.puzzleRef.edition == null) {
                        it
                    } else {
                        it.copy(
                            verdict = RecordVerdict.of(baselineBest, record.time, record.hints, baselinePlayAgain, rankOrder),
                            previousBest = baselineBest,
                        )
                    }
                }
                is AppResult.Err -> _record.update { it.copy(writeFailed = true) }
            }
        }
    }

    private fun PuzzleRef.sameIdentity(other: PuzzleRef) = gameId == other.gameId && key == other.key

    /** The daily's edition number; null for an unnumbered daily or a custom puzzle. */
    private val PuzzleRef.edition: Int? get() = (key as? PuzzleKey.Daily)?.puzzleNumber

    private fun blankRecord(label: String) =
        ActivePlay.Record(verdict = null, previousBest = null, writeFailed = false, label = label, rankOrder = rankOrder)

    private val rankOrder: RankOrder get() = plugin.score.rankOrder

    /** "Zip · #598" for a daily, "Tango · Practice" for a puzzle with no edition number. */
    private fun labelFor(ref: PuzzleRef?): String {
        val name = plugin.definition.displayName
        if (ref == null) return name
        return ref.edition?.let { "$name · #$it" } ?: "$name · Practice"
    }
}
