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
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
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
 * fire-and-observe) and the A4 badge derived from the session-start baseline.
 *
 * Every [load] and restore adopts into a NEW controller; callbacks and async results of
 * an older controller are dropped (the generation guard), so a superseded session can
 * never publish bytes or write a record.
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
) {
    /** What the play screen draws: the live controller and the shared fetch state. */
    data class Board(val controller: GamePlayController, val status: PlayBoardStatus)

    /**
     * What the result route reads. [badge] is the A4 verdict for THIS play — null while
     * the write is in flight or the baseline is unknown; [label] is the result screen's
     * game line ("Zip · #123").
     */
    data class Record(val badge: Boolean?, val writeFailed: Boolean, val label: String)

    val gameId: GameId get() = plugin.definition.id

    private var generation = 0
    private val _board = MutableStateFlow(Board(newController(), PlayBoardStatus.Loading))
    val board: StateFlow<Board> = _board.asStateFlow()

    private val _record = MutableStateFlow(Record(badge = null, writeFailed = false, label = labelFor(null)))
    val record: StateFlow<Record> = _record.asStateFlow()

    val controller: GamePlayController get() = _board.value.controller

    /** A fresh entry must fetch: nothing playable in hand, or the live session is finished. */
    val needsLoad: Boolean
        get() = _board.value.status !is PlayBoardStatus.Ready || controller.isComplete

    private var ref: PuzzleRef? = null

    /** Whether a record already existed for [ref] BEFORE this session's win — the A4
     *  badge is derived from this baseline, never from the stored rows. */
    private var baselineHadRecord: Boolean? = null
    private var lastWon: CompletionRecord? = null

    /** Fetch-today → board (A7/A8): Fresh plays; Cached plays with a degrade banner;
     *  Unavailable is the error state; an envelope the game rejects is never playable. */
    fun load() {
        val controller = newController()
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
     * puzzle BY VALUE, so a corrupt blob falls back to a normal [load]; a restored
     * COMPLETED session shows its win without re-emitting (emission happens on the
     * transition only) and reads no baseline.
     */
    internal fun adoptRestored(bytes: ByteArray) {
        val controller = newController()
        val restored = controller.adoptRestored(bytes) ?: return load()
        resetRecord(restored.ref)
        _board.value = Board(controller, PlayBoardStatus.Ready(banner = null))
        if (!controller.isComplete) refreshBaseline(restored.ref)
    }

    /** Result's "Play again": a fresh session of the SAME puzzle (A9) — the badge resets
     *  until the new write lands and the A4 baseline is re-read. The banner stays. */
    fun replay() {
        if (_board.value.status !is PlayBoardStatus.Ready) return
        resetRecord(ref)
        refreshBaseline(ref)
        controller.replay()
    }

    /** Retry after a failed record write: a new emitter (the old one deduped this ref). */
    fun retryRecordWrite() {
        emit(lastWon ?: return)
    }

    //region internals

    private fun newController(): GamePlayController {
        val gen = ++generation
        return ui.createController(
            PlayEnvironment(
                scope = scope,
                onSessionChanged = { bytes -> if (gen == generation) publish(this, bytes) },
                onWon = { record -> if (gen == generation) onWon(record) },
            ),
        )
    }

    private fun onWon(record: CompletionRecord) {
        lastWon = record
        emit(record)
    }

    private fun emit(record: CompletionRecord) {
        val gen = generation
        _record.update { it.copy(badge = null, writeFailed = false) }
        CompletionRecordEmitter(scope) { r ->
            completions.onCompleted(r)
            AppResult.Ok(Unit)
        }.emit(record) { result ->
            if (gen != generation) return@emit
            when (result) {
                // A4 badge for THIS play: true only when no record existed at session
                // start; unknown baseline (write raced the baseline read) stays null.
                is AppResult.Ok -> _record.update { it.copy(badge = baselineHadRecord?.let { had -> !had }) }
                is AppResult.Err -> _record.update { it.copy(writeFailed = true) }
            }
        }
    }

    private fun resetRecord(ref: PuzzleRef?) {
        this.ref = ref
        baselineHadRecord = null
        lastWon = null
        _record.value = Record(badge = null, writeFailed = false, label = labelFor(ref))
    }

    private fun refreshBaseline(ref: PuzzleRef?) {
        if (ref == null) return
        val gen = generation
        scope.launch {
            val had = completions.observeRecords().first().any { it.puzzleRef == ref }
            if (gen == generation) baselineHadRecord = had
        }
    }

    private fun labelFor(ref: PuzzleRef?): String {
        val name = plugin.definition.displayName
        return ref?.puzzleNumber?.let { "$name · #$it" } ?: name
    }

    //endregion
}
