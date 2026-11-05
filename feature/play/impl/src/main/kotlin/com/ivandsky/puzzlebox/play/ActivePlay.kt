package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.degradeBannerMessage
import com.ivandsky.puzzlebox.gameui.unavailableMessage
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One game's live play (ticket 23): the game's [GamePlayController] plus everything the
 * play feature owns around it — the fetch → adopt lifecycle (A7/A8 degrade states
 * first-class), the record identity (ADR-0004), the record write (F2 §7
 * fire-and-observe) and the A10 verdict derived from the session-start best-time baseline.
 *
 * Every [load] and restore adopts into a NEW controller; callbacks and async results of
 * an older controller are dropped (the generation guard), so a superseded session can
 * never publish bytes or write a record. The one exception is a restore of the session
 * the live controller already holds (rotation): that controller is kept, transient
 * state and all. The record write is tracked apart from the controller (the record
 * token): only [load], [replay] and a restore of ANOTHER puzzle start a new record, so a
 * write in flight across a same-puzzle restore still delivers its verdict or failure.
 */
class ActivePlay internal constructor(
    private val plugin: GamePlugin<*, *, *>,
    /** The game's play registration — draws [controller]. */
    val ui: GamePlayUi,
    private val daily: DailyRepository,
    private val completions: CompletionRepository,
    private val today: () -> LocalDate,
    private val scope: CoroutineScope,
    private val publish: (ActivePlay, ByteArray) -> Unit,
    /** The win transition of the live generation (the store discards the kept session). */
    private val onSolved: (ActivePlay) -> Unit,
) {
    /** What the play screen draws: the live controller and the shared fetch state. */
    data class Board(val controller: GamePlayController, val status: PlayBoardStatus)

    /**
     * What the result route reads. [verdict] is the A10 verdict for THIS play — null while
     * the write is in flight or the baseline is unknown; [previousBestMs] is the game's
     * best before this session (null with no completion, or until [verdict] lands);
     * [label] is the result screen's game line ("Zip · #123").
     */
    data class Record(
        val verdict: RecordVerdict?,
        val previousBestMs: Long?,
        val writeFailed: Boolean,
        val label: String,
    )

    val gameId: GameId get() = plugin.definition.id

    private var generation = 0

    /** The session bytes the live controller last published or was restored from. */
    private var held: ByteArray? = null

    /** The record write's identity: bumped by [resetRecord] only (load/replay, or a
     *  restore of ANOTHER puzzle) — never by a controller swap. */
    private var recordToken = 0

    private val _board = MutableStateFlow(Board(newController(), PlayBoardStatus.Loading))
    val board: StateFlow<Board> = _board.asStateFlow()

    private val _record = MutableStateFlow(blankRecord(labelFor(null)))
    val record: StateFlow<Record> = _record.asStateFlow()

    val controller: GamePlayController get() = _board.value.controller

    /** A fresh entry must fetch: nothing playable in hand, or the live session is finished. */
    val needsLoad: Boolean
        get() = _board.value.status !is PlayBoardStatus.Ready || controller.isComplete

    /** The live session's record day when it is the game's cadence today; null before
     *  an adopt or for a replay of another day (spec A4: only today's session is kept). */
    internal val todayDay: LocalDate? get() = ref?.date?.takeIf { it == today() }

    /**
     * Folds the live clock through the game's own background hook (the same one the
     * chrome calls on ON_PAUSE — folding an already-paused clock banks nothing) and
     * returns the displayed elapsed; null for a game without a clock.
     */
    internal fun foldClock(): Long? {
        val clock = controller as? PlayClock ?: return null
        clock.onBackground()
        return clock.displayedElapsedMs()
    }

    private var ref: PuzzleRef? = null

    /** The game's best time BEFORE this session and whether this puzzle was already
     *  solved (a replay), read at session start (A10): the verdict is derived from this
     *  baseline, never from the stored rows after the write. [baselineKnown] false = the
     *  read has not landed (the verdict stays null). */
    private var baselineKnown = false
    private var baselineBestMs: Long? = null
    private var baselineReplay = false
    private var lastWon: CompletionRecord? = null

    /** Fetch-today → board (A7/A8): Fresh plays; Cached plays with a degrade banner;
     *  Unavailable is the error state; an envelope the game rejects is never playable. */
    fun load() = load(newController())

    private fun load(controller: GamePlayController) {
        val gen = generation
        resetRecord(ref = null)
        _board.value = Board(controller, PlayBoardStatus.Loading)
        scope.launch {
            val status = when (val fetched = daily.today(gameId)) {
                is DailyToday.Fresh -> adopt(controller, fetched.puzzle, banner = null)
                is DailyToday.Cached -> adopt(controller, fetched.puzzle, banner = degradeBannerMessage(fetched.error))
                is DailyToday.Unavailable -> PlayBoardStatus.Unavailable(unavailableMessage(fetched.error))
            }
            if (gen == generation) _board.value = Board(controller, status)
        }
    }

    /** Record identity = game id + day/puzzleNumber (ADR-0004): the repository persists
     *  both at fetch time and history rows carry them; today's row (in the game's
     *  cadence zone) is the identity of the board just fetched. */
    private suspend fun adopt(controller: GamePlayController, puzzle: Puzzle, banner: String?): PlayBoardStatus {
        val day = today()
        val entry = daily.observeHistory(gameId).first().firstOrNull { it.day == day }
        val resolved = plugin.refOf(entry?.day ?: day, entry?.puzzleNumber)
        if (controller !== this.controller) return PlayBoardStatus.Loading // superseded; never shown
        return when (val adopted = controller.adoptToday(puzzle, resolved)) {
            is Adopt.Rejected -> PlayBoardStatus.Unavailable(adopted.message)
            Adopt.Adopted -> {
                resetRecord(resolved)
                refreshBaseline(resolved)
                PlayBoardStatus.Ready(banner)
            }
        }
    }

    /**
     * Rotation/process-death + history-replay restore: the game's codec carries the
     * puzzle BY VALUE, so a corrupt blob falls back to a normal [load] (on the controller
     * that failed to read it — it adopted nothing); a restored COMPLETED session shows
     * its win without re-emitting (emission happens on the transition only) and reads
     * no baseline. The session the live controller already holds (rotation: the store
     * outlived the composition) keeps that controller — hint cooldown, visible hint,
     * rejection and the record write untouched. A restore of the same puzzle keeps the
     * record; another puzzle starts a new one.
     */
    internal fun adoptRestored(bytes: ByteArray) {
        if (holds(bytes)) return
        val controller = newController()
        val restored = controller.adoptRestored(bytes) ?: return load(controller)
        held = bytes
        val newPuzzle = restored.ref != ref
        if (newPuzzle) resetRecord(restored.ref)
        _board.value = Board(controller, PlayBoardStatus.Ready(banner = null))
        if (newPuzzle && !controller.isComplete) refreshBaseline(restored.ref)
    }

    /** True when the live controller already holds exactly [bytes] (rotation's restore). */
    internal fun holds(bytes: ByteArray): Boolean = held?.contentEquals(bytes) == true

    /** Result's "Play again": a fresh session of the SAME puzzle (A9), always a replay —
     *  the verdict resets until the new write lands and the A10 baseline is re-read. The
     *  banner stays. */
    fun replay() {
        if (_board.value.status !is PlayBoardStatus.Ready) return
        resetRecord(ref)
        refreshBaseline(ref, replay = true)
        controller.replay()
    }

    /** Retry after a failed record write: a new emitter (the old one deduped this ref). */
    fun retryRecordWrite() {
        emit(lastWon ?: return)
    }

    //region internals

    private fun newController(): GamePlayController {
        val gen = ++generation
        held = null
        return ui.createController(
            PlayEnvironment(
                scope = scope,
                onSessionChanged = { bytes ->
                    if (gen == generation) {
                        held = bytes
                        publish(this, bytes)
                    }
                },
                onWon = { record -> if (gen == generation) onWon(record) },
            ),
        )
    }

    private fun onWon(record: CompletionRecord) {
        lastWon = record
        onSolved(this)
        emit(record)
    }

    private fun emit(record: CompletionRecord) {
        val token = recordToken
        _record.update { it.copy(verdict = null, previousBestMs = null, writeFailed = false) }
        CompletionRecordEmitter(scope) { r ->
            completions.onCompleted(r)
            AppResult.Ok(Unit)
        }.emit(record) { result ->
            if (token != recordToken) return@emit
            when (result) {
                // A10 verdict for THIS play against the session-start best; an unknown
                // baseline (the write raced the baseline read) stays null.
                is AppResult.Ok -> _record.update {
                    if (!baselineKnown) {
                        it
                    } else {
                        it.copy(
                            verdict = RecordVerdict.of(baselineBestMs, record.time, baselineReplay),
                            previousBestMs = baselineBestMs,
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
        baselineBestMs = null
        baselineReplay = false
        lastWon = null
        _record.value = blankRecord(labelFor(ref))
    }

    /** [replay] true = known without a read (Play again: the solve's write may still be in
     *  flight). Otherwise a record row with this ref's identity (ADR-0004) makes it one. */
    private fun refreshBaseline(ref: PuzzleRef?, replay: Boolean = false) {
        if (ref == null) return
        val token = recordToken
        scope.launch {
            val best = completions.bestTimeMs(ref.gameId)
            val solved = replay || completions.observeRecords().first().any { it.puzzleRef.sameIdentity(ref) }
            if (token == recordToken) {
                baselineBestMs = best
                baselineReplay = solved
                baselineKnown = true
            }
        }
    }

    private fun PuzzleRef.sameIdentity(other: PuzzleRef) =
        gameId == other.gameId && date == other.date && puzzleNumber == other.puzzleNumber

    private fun blankRecord(label: String) =
        Record(verdict = null, previousBestMs = null, writeFailed = false, label = label)

    private fun labelFor(ref: PuzzleRef?): String {
        val name = plugin.definition.displayName
        return ref?.puzzleNumber?.let { "$name · #$it" } ?: name
    }

    //endregion
}
