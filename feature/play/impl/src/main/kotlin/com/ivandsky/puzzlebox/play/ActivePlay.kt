package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
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
    /** Read at each open: a debug override applies to the next play, never mid-play. */
    private val recordsReplay: () -> Boolean,
) {
    /** What the play screen draws: the live controller and the shared fetch state. */
    data class Board(val controller: GamePlayController, val status: PlayBoardStatus)

    /**
     * What the result route reads. [verdict] is the verdict for THIS play — null while
     * the write is in flight or the baseline is unknown; [previousBest] is the game's best
     * before this session by [rankOrder] (null with no completion, or until [verdict] lands);
     * [label] is the result screen's game line ("Zip · #123"); [solve] is the win this
     * record belongs to, null until this session wins (a load or play again clears it).
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

    private val persistence = PlayPersistence(slot, writer)
    private val winRecording = WinRecording(plugin, completions, scope, recordScope)

    private val _board = MutableStateFlow(Board(newController(), PlayBoardStatus.Loading))
    val board: StateFlow<Board> = _board.asStateFlow()

    val record: StateFlow<Record> get() = winRecording.record

    val controller: GamePlayController get() = _board.value.controller

    private var ref: PuzzleRef? = null

    /** Reopens the adopted puzzle in another controller; null before the first adopt. Play again of a recording play needs it. */
    private var reopen: ((GamePlayController) -> Boolean)? = null

    /** A new record starts: a load, its adopt or resume, and play again. */
    private fun resetRecord(ref: PuzzleRef?) {
        this.ref = ref
        winRecording.reset(ref)
    }

    /** Opening → board: a restored session resumes; a Fresh fetch plays; a
     *  Cached one plays with a degrade banner; Unavailable is the error state; an envelope
     *  the game rejects is never playable. */
    fun load() = load(newController())

    /** The error state's retry: opens the slot again. */
    fun retry() = load()

    private fun load(controller: GamePlayController) {
        val gen = generation
        resetRecord(ref = null)
        reopen = null
        _board.value = Board(controller, PlayBoardStatus.Loading)
        scope.launch {
            val opened = opening.open(slot)
            if (gen != generation) return@launch // superseded; its result is never shown
            val live = when (opened) {
                is SlotOpening.Origin.Unavailable -> controller
                is SlotOpening.Origin.Restored -> withLog(controller, fresh = false)
                is SlotOpening.Origin.Fetched, is SlotOpening.Origin.Built -> withLog(controller, fresh = true)
            }
            val status = when (opened) {
                is SlotOpening.Origin.Fetched -> adopt(live, opened)
                is SlotOpening.Origin.Restored -> resume(live, opened.session)
                is SlotOpening.Origin.Built -> resume(live, opened.session)
                is SlotOpening.Origin.Unavailable -> PlayBoardStatus.Unavailable(opened.message, opened.retryable)
            }
            _board.value = Board(live, status)
        }
    }

    /** Record identity = game id + day/puzzleNumber (ADR-0004): the slot's day and the
     *  number the opening read from the cache's history rows; a friend's puzzle is its code. */
    private fun adopt(controller: GamePlayController, fetched: SlotOpening.Origin.Fetched): PlayBoardStatus {
        val resolved = when (val slot = slot) {
            is PlaySlot.Daily -> plugin.refOf(PuzzleKey.Daily(slot.day, fetched.puzzleNumber))
            is PlaySlot.Custom -> plugin.refOf(PuzzleKey.Custom(slot.puzzleId))
            is PlaySlot.Leg -> error("legs open Built")
        }
        if (controller !== this.controller) return PlayBoardStatus.Loading // superseded; never shown
        return when (val adopted = controller.adoptToday(fetched.puzzle, resolved)) {
            is Adopt.Rejected -> PlayBoardStatus.Unavailable(adopted.message)
            Adopt.Adopted -> {
                reopen = { it.adoptToday(fetched.puzzle, resolved) == Adopt.Adopted }
                resetRecord(resolved)
                winRecording.refreshBaseline(resolved)
                PlayBoardStatus.Ready(fetched.banner)
            }
        }
    }

    /** A saved play or a built session (past day, duel leg): the [SessionBlob] envelope the opening
     *  checked. Bytes the controller still cannot read are unavailable, never a loop. A
     *  restored COMPLETED session shows its win without re-emitting and reads no baseline. */
    private fun resume(controller: GamePlayController, envelope: ByteArray): PlayBoardStatus {
        if (controller !== this.controller) return PlayBoardStatus.Loading // superseded; never shown
        val session = SessionBlob.decode(envelope)?.session ?: return PlayBoardStatus.Unavailable(UNREADABLE)
        val restored = controller.adoptRestored(session) ?: return PlayBoardStatus.Unavailable(UNREADABLE)
        reopen = { it.adoptRestored(session) != null }
        resetRecord(restored.ref)
        if (!controller.isComplete) winRecording.refreshBaseline(restored.ref)
        return PlayBoardStatus.Ready(banner = null)
    }

    /** Result's "Play again": a fresh session of the SAME puzzle, always a play-again —
     *  the verdict resets until the new write lands; the best is re-read. The banner stays.
     *  A recording play starts a new log, so it moves to a new controller over the same puzzle. */
    fun playAgain() {
        if (_board.value.status !is PlayBoardStatus.Ready) return
        resetRecord(ref)
        winRecording.refreshBaseline(ref, playAgain = true)
        val reopen = reopen
        if (reopen == null || !recording) return controller.playAgain()
        val next = newController(MoveLog.fresh(gameId))
        check(reopen(next)) { "play again could not reopen the puzzle this play adopted" }
        _board.value = _board.value.copy(controller = next)
        next.playAgain()
    }

    /** Retry after a failed record write: a new emitter (the old one deduped this ref). */
    fun retryRecordWrite() = winRecording.retry()

    /** App stop: folds the clock and waits until the slot's pending save is written. */
    internal suspend fun save() = persistence.save(controller)

    /** Release: the final save is written, then the play's own work stops; [save] false
     *  drops it instead (pending save cancelled). The record write on [recordScope] outlives it. */
    internal suspend fun close(save: Boolean = true) {
        if (save) save() else persistence.discard()
        scope.cancel()
    }

    /** The slot's newest session change its writer still holds (not yet queued for disk). */
    internal val unsavedSession: ByteArray? get() = persistence.unsaved

    private fun newController(moveLog: MoveLog = MoveLog.off()): GamePlayController {
        val gen = ++generation
        lateinit var created: GamePlayController
        created = ui.createController(
            PlayEnvironment(
                scope = scope,
                onSessionChanged = { bytes -> if (gen == generation) persistence.offer(created, bytes) },
                onWon = { record ->
                    if (gen == generation) {
                        persistence.discard()
                        winRecording.onWon(record)
                    }
                },
                moveLog = moveLog,
            ),
        )
        return created
    }

    /** Whether this play records a replay: the flag is on and the game ships a replay member. */
    private val recording: Boolean get() = plugin.replay != null && recordsReplay()

    /**
     * The controller to adopt into once the origin is known: [current] while nothing records, else a new
     * one whose environment carries the log the origin calls for — a built or fetched board starts
     * [MoveLog.fresh], a saved play continues through [MoveLog.restored]. A log lives as long as its
     * controller, so the board shows the new one. A superseded [current] is returned untouched.
     */
    private fun withLog(current: GamePlayController, fresh: Boolean): GamePlayController {
        if (current !== controller || !recording) return current
        val log = if (fresh) MoveLog.fresh(gameId) else MoveLog.restored(gameId, snapshot = null)
        return newController(log).also { _board.value = Board(it, PlayBoardStatus.Loading) }
    }

    private companion object {
        const val UNREADABLE = "This saved game couldn't be read."
    }
}
