package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One slot's live play: the game's [GamePlayController] plus everything the
 * play feature owns around it — the opening → adopt lifecycle (degrade states
 * first-class, restore order through [opening]), the record identity (ADR-0004), the
 * record write (fire-and-observe, on [recordScope] so a release never cancels it),
 * the verdict derived from the session-start best baseline, and the slot's saves: every
 * in-progress session change is offered to [writer], a solve discards the row.
 *
 * Every [load] adopts into a NEW controller; callbacks and async results of an older
 * controller are dropped (the generation guard), so a superseded session can never
 * save bytes or write a record. The record write is tracked apart from the controller
 * (the record token): only [load] and [playAgain] start a new record.
 */
class ActivePlay internal constructor(
    /** The (game, day) this is the one live play of. */
    val slot: PlaySlot,
    private val plugin: GamePlugin<*, *, *>,
    /** The game's play registration — draws [controller]. */
    val ui: GamePlayUi,
    /** Where each [load]'s board comes from; asked again on every load and retry. */
    private val opening: SlotOpening,
    /** The slot's saves. */
    private val writer: SlotWriter,
    private val completions: CompletionRepository,
    private val scope: CoroutineScope,
    private val recordScope: CoroutineScope,
) {
    /** What the play screen draws: the live controller and the shared fetch state. */
    data class Board(val controller: GamePlayController, val status: PlayBoardStatus)

    /**
     * What the result route reads. [verdict] is the verdict for THIS play — null while
     * the write is in flight or the baseline is unknown; [previousBest] is the game's best
     * before this session by [rankOrder] (null with no completion, or until [verdict] lands);
     * [label] is the result screen's game line ("Zip · #123"); [solve] is the win this
     * record belongs to, null until this session wins (a load or replay clears it).
     */
    data class Record(
        val verdict: RecordVerdict?,
        val previousBest: BestScore?,
        val writeFailed: Boolean,
        val label: String,
        val rankOrder: RankOrder,
        val solve: Solve? = null,
    )

    /** A win's frozen facts, as the result key carries them. */
    data class Solve(val timeMs: Long, val hints: Int)

    val gameId: GameId get() = plugin.definition.id

    private var generation = 0

    /** The record write's identity: bumped by [resetRecord] only (a load, its adopt or
     *  resume, and replay) — never by a controller swap. */
    private var recordToken = 0

    private val _board = MutableStateFlow(Board(newController(), PlayBoardStatus.Loading))
    val board: StateFlow<Board> = _board.asStateFlow()

    private val _record = MutableStateFlow(blankRecord(labelFor(null)))
    val record: StateFlow<Record> = _record.asStateFlow()

    val controller: GamePlayController get() = _board.value.controller

    private var ref: PuzzleRef? = null

    /** The game's best BEFORE this session and whether this puzzle was already
     *  solved (a replay), read at session start: the verdict is derived from this
     *  baseline, never from the stored rows after the write. [baselineKnown] false = the
     *  read has not landed (the verdict stays null). */
    private var baselineKnown = false
    private var baselineBest: BestScore? = null
    private var baselinePlayAgain = false
    private var lastWon: CompletionRecord? = null

    /** Opening → board: a restored session resumes; a Fresh fetch plays; a
     *  Cached one plays with a degrade banner; Unavailable is the error state; an envelope
     *  the game rejects is never playable. */
    fun load() = load(newController())

    /** The error state's retry: opens the slot again. */
    fun retry() = load()

    private fun load(controller: GamePlayController) {
        val gen = generation
        resetRecord(ref = null)
        _board.value = Board(controller, PlayBoardStatus.Loading)
        scope.launch {
            val status = when (val opened = opening.open(slot)) {
                is SlotOpening.Origin.Fetched -> adopt(controller, opened)
                is SlotOpening.Origin.Restored -> resume(controller, opened.session)
                is SlotOpening.Origin.Unavailable -> PlayBoardStatus.Unavailable(opened.message, opened.retryable)
            }
            if (gen == generation) _board.value = Board(controller, status)
        }
    }

    /** Record identity = game id + day/puzzleNumber (ADR-0004): the slot's day and the
     *  number the opening read from the cache's history rows; a friend's puzzle is its code. */
    private fun adopt(controller: GamePlayController, fetched: SlotOpening.Origin.Fetched): PlayBoardStatus {
        val resolved = when (val slot = slot) {
            is PlaySlot.Daily -> plugin.refOf(PuzzleKey.Daily(slot.day, fetched.puzzleNumber))
            is PlaySlot.Custom -> plugin.refOf(PuzzleKey.Custom(slot.puzzleId))
            is PlaySlot.Leg -> error("legs open Restored")
        }
        if (controller !== this.controller) return PlayBoardStatus.Loading // superseded; never shown
        return when (val adopted = controller.adoptToday(fetched.puzzle, resolved)) {
            is Adopt.Rejected -> PlayBoardStatus.Unavailable(adopted.message)
            Adopt.Adopted -> {
                resetRecord(resolved)
                refreshBaseline(resolved)
                PlayBoardStatus.Ready(fetched.banner)
            }
        }
    }

    /** A saved play or a past day's replay build: the [SessionBlob] envelope the opening
     *  checked. Bytes the controller still cannot read are unavailable, never a loop. A
     *  restored COMPLETED session shows its win without re-emitting and reads no baseline. */
    private fun resume(controller: GamePlayController, envelope: ByteArray): PlayBoardStatus {
        if (controller !== this.controller) return PlayBoardStatus.Loading // superseded; never shown
        val restored = SessionBlob.decode(envelope)?.session?.let(controller::adoptRestored)
            ?: return PlayBoardStatus.Unavailable(UNREADABLE)
        resetRecord(restored.ref)
        if (!controller.isComplete) refreshBaseline(restored.ref)
        return PlayBoardStatus.Ready(banner = null)
    }

    /** Result's "Play again": a fresh session of the SAME puzzle, always a replay —
     *  the verdict resets until the new write lands; the best is re-read. The banner stays. */
    fun playAgain() {
        if (_board.value.status !is PlayBoardStatus.Ready) return
        resetRecord(ref)
        refreshBaseline(ref, playAgain = true)
        controller.playAgain()
    }

    /** Retry after a failed record write: a new emitter (the old one deduped this ref). */
    fun retryRecordWrite() {
        emit(lastWon ?: return)
    }

    /** Banks the running clock window so the next save carries it. */
    internal fun fold() {
        (controller as? PlayClock)?.onBackground()
    }

    /** App stop: folds the clock and waits until the slot's pending save is written. */
    internal suspend fun save() {
        fold()
        writer.flush()
    }

    /** Release: the final save is written, then the play's own work stops; [save] false
     *  drops it instead (pending save cancelled). The record write on [recordScope] outlives it. */
    internal suspend fun close(save: Boolean = true) {
        if (save) save() else writer.discard()
        scope.cancel()
    }

    /** The slot's newest session change its writer still holds (not yet queued for disk). */
    internal val unsavedSession: ByteArray? get() = writer.unsaved

    //region internals

    private fun newController(): GamePlayController {
        val gen = ++generation
        lateinit var created: GamePlayController
        created = ui.createController(
            PlayEnvironment(
                scope = scope,
                onSessionChanged = { bytes ->
                    if (gen == generation) {
                        // A finished session never saves: the solve deleted the row.
                        if (!created.isComplete) {
                            val elapsedMs = (created as? PlayClock)?.displayedElapsedMs() ?: 0L
                            writer.offer(SessionBlob.encode(slot.gameId, bytes), elapsedMs)
                        }
                    }
                },
                onWon = { record -> if (gen == generation) onWon(record) },
            ),
        )
        return created
    }

    private fun onWon(record: CompletionRecord) {
        writer.discard()
        lastWon = record
        _record.update { it.copy(solve = Solve(record.time, record.hints)) }
        emit(record)
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

    private fun resetRecord(ref: PuzzleRef?) {
        recordToken++
        this.ref = ref
        baselineKnown = false
        baselineBest = null
        baselinePlayAgain = false
        lastWon = null
        _record.value = blankRecord(labelFor(ref))
    }

    /** [playAgain] true = known without a read (Play again: the solve's write may still be in
     *  flight). Otherwise a record row with this ref's identity (ADR-0004) makes it one. */
    private fun refreshBaseline(ref: PuzzleRef?, playAgain: Boolean = false) {
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

    private fun PuzzleRef.sameIdentity(other: PuzzleRef) = gameId == other.gameId && key == other.key

    /** The daily's edition number; null for an unnumbered daily or a custom puzzle. */
    private val PuzzleRef.edition: Int? get() = (key as? PuzzleKey.Daily)?.puzzleNumber

    private fun blankRecord(label: String) =
        Record(verdict = null, previousBest = null, writeFailed = false, label = label, rankOrder = rankOrder)

    private val rankOrder: RankOrder get() = plugin.score.rankOrder

    /** "Zip · #598" for a daily, "Tango · Practice" for a puzzle with no edition number. */
    private fun labelFor(ref: PuzzleRef?): String {
        val name = plugin.definition.displayName
        if (ref == null) return name
        return ref.edition?.let { "$name · #$it" } ?: "$name · Practice"
    }

    private companion object {
        const val UNREADABLE = "This saved game couldn't be read."
    }

    //endregion
}
