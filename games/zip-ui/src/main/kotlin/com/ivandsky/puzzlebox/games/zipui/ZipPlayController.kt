package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.BROKEN_PUZZLE_MESSAGE
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayFeedback
import com.ivandsky.puzzlebox.gameui.PlayHints
import com.ivandsky.puzzlebox.gameui.PlayUndo
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipHint
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import java.time.Instant

/**
 * The Zip play controller (ticket 13): one adopted board → engine → win transition. Owns
 * the [ZipSession] as the single source of truth for path/hints/timer; every committed
 * change round-trips through [PlayEnvironment.onSessionChanged] as session-codec bytes so
 * the play feature can persist them across process death and rotation (A19 session
 * codec, F2 §2 timer semantics).
 *
 * Win (F2 §7): the [ZipSession.completedAt] transition hands ONE completion record to
 * [PlayEnvironment.onWon]; the play feature writes it and derives the A4 badge.
 */
class ZipPlayController(
    private val env: PlayEnvironment,
    private val now: () -> Instant = Instant::now,
    /** Ticket 06: the refusal/trim messages the shared caption slot shows. */
    val feedback: ZipFeedback = ZipFeedback(env.scope),
) : GamePlayController, PlayClock, PlayHints, PlayUndo, PlayFeedback by feedback {

    var session by mutableStateOf<ZipSession?>(null)
        private set
    var hintCell by mutableStateOf<Int?>(null)
        private set

    /** Ticket 28: the hint arrow's placed segment (tail → new head), transient — gone on
     *  the next input or after ~2s (the screen fades it via [clearHintArrow]). */
    var hintArrow by mutableStateOf<HintArrow?>(null)

    /** Hint telemetry (user, 2026-11-02): one event per accepted press — the trace head
     *  when the user asked and the cell the hint placed. In-memory this session;
     *  persistence is a future call. Cleared on play-again (fresh session, fresh log). */
    val hintHistory = mutableStateListOf<HintEvent>()

    var nowMs by mutableStateOf(0L)
        private set

    override val isComplete: Boolean get() = session?.isComplete == true

    private val rules = zipPlugin.rules as ZipRules // the plugin's rules ARE ZipRules

    private val log = ZipMoveLog(env.moveLog, now)

    //region input (F2 §7: candidate moves in, authoritative validation here)

    fun onDragEvent(event: DragEvent) {
        hintArrow = null // ticket 28: the arrow dies on the next input, always
        val current = session ?: return
        if (current.isComplete) return
        val board = current.puzzle.board
        when (event) {
            is DragEvent.Extend -> {
                val move = ZipMove.Extend(cellOf(event.cell, board.n))
                when (val verdict = rules.validateMove(move, ZipState(current.puzzle, current.path))) {
                    is ZipValidation.Valid -> commit(log.record(current, ZipReplayMove.Extend(move.to)))
                    is ZipValidation.Invalid -> reject(current, cellOf(event.cell, board.n), verdict.reason)
                }
            }

            is DragEvent.Retract -> {
                val target = current.path.getOrNull(event.index) ?: return
                commit(log.record(current, ZipReplayMove.RetractTo(target)))
            }

            // R1 tap on the first traced cell: the trace empties to the start state.
            DragEvent.Clear -> edits.clear()
            // A7: the board refused a cell before it became a move — explain, never count.
            is DragEvent.Refused -> feedback.refused(current, cellOf(event.cell, board.n))
        }
    }

    //region undo / redo / clear (core PlayUndo + PlayClear, over the session's history)

    /** The trace edits: each step drops the hint arrow (ticket 28: any input does) and
     *  persists through [commit] like any move. */
    internal val edits = ZipTraceEdits({ session }, log) { hintArrow = null; commit(it) }

    override val canUndo: Boolean get() = edits.canUndo

    override val canRedo: Boolean get() = edits.canRedo

    override fun undo() = edits.undo()

    override fun redo() = edits.redo()

    //endregion

    //region adopt / restore

    /** Today's envelope under [ref]; an untrustable payload is never playable (A16). */
    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        val zip = when (val decoded = ZipCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return Adopt.Rejected(BROKEN_PUZZLE_MESSAGE)
        }
        session = ZipSession(ref = ref as ZipPuzzleRef, puzzle = zip).onForeground(now())
        return Adopt.Adopted
    }

    /** Rotation/process-death restore: the codec carries the puzzle BY VALUE; a COMPLETED
     *  session shows the win without re-emitting. The window is process-local, so an
     *  in-progress session reopens it now. */
    override fun adoptRestored(bytes: ByteArray): Restored? =
        when (val restored = ZipSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                session = restored.value.onForeground(now())
                Restored(restored.value.ref)
            }

            is DecodeResult.Err -> null
        }

    //endregion

    /**
     * Hint (A3, F2 §10, ticket 28): a provably dead trace is trimmed FREE (the longest
     * solution-consistent prefix), then the ONE counted hint AUTO-PLACES the next
     * solution-consistent segment and an arrow marks the new head until the next input
     * or ~2s ([clearHintArrow] — the screen fades it). The highlight survives until the
     * next accepted move. The degenerate first placement (empty trace) places the start
     * cell with no arrow — a lone cell has no segment direction.
     */
    fun hint() {
        val current = session ?: return
        if (current.isComplete) return
        val puzzle = current.puzzle
        val fromIdx = current.path.lastOrNull()?.idx
        var next = current
        var kept: List<Cell>? = null // the trimmed prefix, when the hint fixed a dead trace
        var resolved = rules.hint(ZipState(puzzle, next.path))
        if (resolved is ZipHint.Backtrack) {
            next = log.record(
                next,
                if (resolved.trimTo < 0) ZipReplayMove.Reset else ZipReplayMove.RetractTo(next.path[resolved.trimTo]),
            )
            kept = next.path
            resolved = rules.hint(ZipState(puzzle, next.path))
        }
        // Counted before the step: a hint that places the last cell wins, and a won session takes no count.
        next = log.record(next, ZipReplayMove.Hint)
        val step = resolved as? ZipHint.Step
        var placedIdx: Int? = null
        if (step != null) {
            val move = ZipMove.Extend(step.cell)
            if (rules.validateMove(move, ZipState(puzzle, next.path)) is ZipValidation.Valid) {
                next = log.record(next, ZipReplayMove.Extend(step.cell))
                placedIdx = step.cell.idx
                next.path.getOrNull(next.path.size - 2)?.let { tail ->
                    hintArrow = HintArrow(headIdx = step.cell.idx, tailIdx = tail.idx)
                }
            }
        }
        commit(next)
        kept?.let { feedback.trimmed(puzzle.board, it) }
        hintCell = (resolved as? ZipHint.Step)?.cell?.idx
        val atMs = now().toEpochMilli()
        hintHistory.add(HintEvent(atMs = atMs, fromIdx = fromIdx, placedIdx = placedIdx))
    }

    //region PlayHints (core chrome seam over hint())

    override val hintsUsed: Int get() = session?.hintsUsed ?: 0

    override val hintEnabled: Boolean get() = session?.isComplete == false

    /** Zip's hint is the placed arrow; prose only when the free trim fixed the trace. */
    override val hintCaption: String? get() = feedback.hintCaption

    override fun requestHint() = hint()

    //endregion

    /** The screen calls this after the hold/fade window (reduced motion: immediately). */
    fun clearHintArrow() {
        hintArrow = null
    }

    //region timer (F2 §2: active foreground play only)

    override fun tick() {
        nowMs = now().toEpochMilli()
    }

    override fun onBackground() {
        val current = session?.takeIf { it.runningSince != null } ?: return
        updateSession(current.onBackground(now()))
    }

    override fun onForeground() {
        val current = session ?: return
        current.onForeground(now()).takeIf { it !== current }?.let(::updateSession)
    }

    /** The running window's identity — the loop restarts each time it (re)opens. */
    override val clockWindow: Any?
        get() = session?.runningSince

    /** What TimerBar draws right now (banked + running window). Never negative:
     *  the first frame after the initial Extend observes runningSince before the
     *  tick loop's first tick() advances nowMs. */
    override fun displayedElapsedMs(): Long =
        session?.displayedElapsedMs(Instant.ofEpochMilli(nowMs))?.coerceAtLeast(0L) ?: 0L

    //endregion

    /** Result's "Play again": a fresh session of the same puzzle, every transient reveal
     *  and the hint log cleared (F9), clock running at once; the next win emits again. */
    override fun playAgain() {
        val current = session ?: return
        hintCell = null
        hintArrow = null
        feedback.clear()
        hintHistory.clear()
        commit(ZipSession(ref = current.ref, puzzle = current.puzzle).onForeground(now()))
    }

    //region derived + persistence

    /** The renderer model the board draws — built here (F2: the state→model call site). */
    fun rendererModel(): ZipRendererModel? {
        val current = session ?: return null
        val hint = hintCell?.let { ZipHint.Step(Cell(it)) }
        return ZipRendererModel.of(ZipState(current.puzzle, current.path), hint, feedback.rejectedMove)
    }

    /** Versioned session bytes (A19/F2 §2) — what [PlayEnvironment.onSessionChanged] receives. */
    fun persist(): ByteArray? = session?.let(ZipSessionCodec::serialize)

    //endregion

    //region internals

    /** An accepted change: the hint highlight and every caption message retire. */
    private fun commit(next: ZipSession) {
        hintCell = null
        feedback.onAccepted()
        updateSession(next)
    }

    private fun updateSession(next: ZipSession) {
        val won = next.isComplete && session?.isComplete != true
        session = next
        env.onSessionChanged(ZipSessionCodec.serialize(next))
        if (won) env.onWon(zipPlugin.score.completion(next) ?: return)
    }

    private fun reject(current: ZipSession, cell: Cell, reason: ZipValidation.Reason) {
        updateSession(current.withRejection()) // mistake analytics only, never scored (A3)
        feedback.reject(current.puzzle.board, cell, reason)
    }

    private fun cellOf(cell: GridCell, n: Int) = Cell(cell.row * n + cell.col)

    //endregion
}

/** The hint arrow's geometry (ticket 28): the placed segment tail → new head, as row-major indices. */
data class HintArrow(val headIdx: Int, val tailIdx: Int)

/** Internal hint telemetry (2026-11-02): trace head at request time and the placed
 *  cell (null = the press trimmed/placed nothing). Wall-clock ms of the state's nowMs. */
data class HintEvent(val atMs: Long, val fromIdx: Int?, val placedIdx: Int?)
