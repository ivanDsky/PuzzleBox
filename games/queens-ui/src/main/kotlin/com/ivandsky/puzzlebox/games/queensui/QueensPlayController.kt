package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.BROKEN_PUZZLE_MESSAGE
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayFeedback
import com.ivandsky.puzzlebox.gameui.PlayGuide
import com.ivandsky.puzzlebox.gameui.PlayHints
import com.ivandsky.puzzlebox.gameui.PlayUndo
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * The Queens play controller (queens-10 wiring; Zip controller shape): one adopted board
 * → a real [QueensSession] (queens-04) → the queens-08 [QueensBoardState] interaction
 * holder. The controller owns only the adopt / restore / clock wiring — every game
 * decision stays in the session and the board state (A10–A12 provenance, A14
 * Auto-cross, §5 interaction set, command undo).
 *
 * Win (fix round 6 — zip's win→result-route pattern): the board state builds ONE
 * completion record (the plugin's score model on the completedAt transition) and this
 * controller hands it to [PlayEnvironment.onWon]; the play feature writes it. A
 * restored completed session never re-emits (transition-only).
 *
 * Timer (Tango parity): the session banks [QueensSession.elapsedMs]; this controller
 * owns the process-local running window, so undo/redo never touch it.
 *
 * Session persistence (R-4): every committed session reaches
 * [PlayEnvironment.onSessionChanged] as codec bytes, including the initial session of a
 * fresh adopt/replay so a process death before the first move still restores.
 *
 * Guided lesson (spec A12): [guide] marks the adopted board's answer with faint crowns
 * and holds back the rule caption ([violation]) until the lesson is done.
 */
class QueensPlayController(
    private val env: PlayEnvironment,
    /** The player's Queens settings; null for a tutorial lesson, which keeps Auto-cross on. */
    private val settings: QueensSettings?,
    private val now: () -> Instant = Instant::now,
    /** Read once at creation (spec A20); gives every board the Spotlight hint (28). */
    private val hintMode: HintMode.Stepwise,
) : GamePlayController, PlayClock, PlayHints, PlayUndo, PlayFeedback, PlayGuide {

    /** The live interaction holder, null until a session is adopted. */
    var board by mutableStateOf<QueensBoardState?>(null)
        private set

    /**
     * Auto-cross as the player set it (on until switched off in the ⋮ menu), seeded from the stored
     * value so a restored board's first frame is right. It follows [settings] live: every board,
     * fresh or restored, derives under it, and a flip re-derives the open board at once. A lesson
     * ([settings] null) stays on.
     */
    var autoCross by mutableStateOf(settings?.autoCross?.value ?: true)
        private set

    init {
        settings?.let { s -> env.scope.launch { s.autoCross.collect(::applyAutoCross) } }
    }

    /** The ⋮ menu's Auto-cross item: the board repaints now, the write only makes it stick. */
    fun chooseAutoCross(enabled: Boolean) {
        applyAutoCross(enabled)
        val s = settings ?: return
        env.scope.launch { s.setAutoCross(enabled) } // FIFO on the main-confined scope: writes land in tap order
    }

    private fun applyAutoCross(enabled: Boolean) {
        autoCross = enabled
        board?.setAutoCross(enabled)
    }

    override val isComplete: Boolean get() = board?.session?.isComplete == true

    /** The open running window's start: null = clock stopped (not started, backgrounded, or won). */
    private var runningSince: Instant? by mutableStateOf(null)

    /** Last [tick]; the window's displayed length is measured against it so the timer recomposes. */
    var nowMs by mutableStateOf(0L)
        private set

    //region adopt / restore

    /** Today's envelope under [ref]; an untrustable payload is never playable (A16). */
    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        val queens = when (val decoded = QueensCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return Adopt.Rejected(BROKEN_PUZZLE_MESSAGE)
        }
        val fresh = QueensSession(ref = ref as QueensPuzzleRef, puzzle = queens, autoCross = autoCross)
        board = boardFor(fresh)
        publish(fresh)
        openWindow() // the clock runs from the moment the puzzle opens
        return Adopt.Adopted
    }

    /** Process-death/rotation restore: the codec carries the puzzle BY VALUE; a restored
     *  COMPLETED session shows the result without re-emitting (emission happens on the
     *  transition only). */
    override fun adoptRestored(bytes: ByteArray): Restored? =
        when (val restored = QueensSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value.withAutoCross(autoCross) // the saved board re-derives under the setting
                runningSince = null
                board = boardFor(s)
                // The running clock is process-local (runningSince is not session state):
                // a restored in-progress session restarts its window now, or the timer
                // reads frozen forever (Tango parity, user QA 2026-11-02).
                if (!s.isComplete) openWindow()
                Restored(s.ref)
            }

            is DecodeResult.Err -> null
        }

    /** A fresh holder per board (adopt, restore, replay); the old one's search in flight is dropped first, so it can
     *  neither show nor save the old session after the swap (A10). */
    private fun boardFor(session: QueensSession): QueensBoardState {
        board?.stepHints?.reset()
        return QueensBoardState(
            initialSession = session,
            score = queensPlugin.score,
            onWinRecord = { env.onWon(it) },
            now = now,
            onSessionChanged = ::publish,
            settle = this::settle,
            mode = hintMode,
            scope = env.scope,
        )
    }

    private fun publish(session: QueensSession) {
        env.onSessionChanged(QueensSessionCodec.serialize(session))
    }

    /** Result's "Play again" parity (zip replay): a fresh session of the same puzzle with
     *  a fresh clock window open — the next win is a new transition and emits again (A4). */
    override fun replay() {
        val current = board?.session ?: return
        runningSince = null
        val fresh = QueensSession(ref = current.ref, puzzle = current.puzzle, autoCross = autoCross)
        board = boardFor(fresh)
        publish(fresh)
        openWindow()
    }

    /** Spec A12: the adopted board shows its answer and no rule caption (tutorial only). */
    override fun guide() {
        checkNotNull(board) { "guide() needs an adopted board" }.guide()
    }

    //endregion

    //region PlayClock (F2 §2: active foreground play only)

    /**
     * The board's hook on every committed session, BEFORE it is stored and the win record
     * built: the win folds the running window into the banked [QueensSession.elapsedMs]
     * so the frozen result and the record carry the real duration. The window itself is
     * opened by adopt/restore/replay/foreground, never by a move.
     */
    private fun settle(next: QueensSession): QueensSession =
        if (next.isComplete) foldRunning(next) else next

    private fun openWindow() {
        val start = now()
        nowMs = start.toEpochMilli()
        runningSince = start
    }

    /** Folds the running window into [target]'s banked elapsed and stops the clock. */
    private fun foldRunning(target: QueensSession): QueensSession {
        val rs = runningSince ?: return target
        runningSince = null
        return target.copy(elapsedMs = target.elapsedMs + Duration.between(rs, now()).toMillis())
    }

    override fun tick() {
        nowMs = now().toEpochMilli()
    }

    override fun onBackground() {
        val rs = runningSince ?: return
        runningSince = null
        board?.bankElapsed(Duration.between(rs, now()).toMillis())
    }

    override fun onForeground() {
        val current = board?.session ?: return
        if (runningSince == null && !current.isComplete) openWindow()
    }

    /** The running window's identity — the loop restarts each time it (re)opens. */
    override val clockWindow: Any?
        get() = runningSince

    /** What TimerBar draws right now (banked + running window). Never negative. */
    override fun displayedElapsedMs(): Long {
        val banked = board?.session?.elapsedMs ?: return 0L
        val window = runningSince?.let { Duration.between(it, Instant.ofEpochMilli(nowMs)).toMillis() } ?: 0L
        return (banked + window).coerceAtLeast(0L)
    }

    //endregion

    //region PlayHints / PlayUndo

    override val hintsUsed: Int get() = board?.session?.hintsUsed ?: 0

    override val hintEnabled: Boolean
        get() = board?.session?.isComplete == false

    /** Queens shows its hint as a Spotlight, never as a chrome caption (A11). */
    override val hintCaption: String? get() = null

    /** A14 always-on conflict line (R-2): live while any rule break stands; derived from
     *  the observable board session, so the shared caption slot recomposes with it. A
     *  guided lesson has no rule yet, so it never shows the line. While a Spotlight shows,
     *  its callout is the one message on screen (A11). */
    override val violation: String?
        get() = board?.takeIf { it.spotlight == null }?.let { b ->
            RULE_CONFLICT.takeIf {
                QueensBoardModel.of(b.session, b.guide).violations.isNotEmpty()
            }
        }

    /** Queens refuses no input: every tap is a legal mark/queen cycle step. */
    override val rejection: String? get() = null

    override fun requestHint() {
        board?.requestHint()
    }

    override val canUndo: Boolean get() = board?.canUndo ?: false
    override val canRedo: Boolean get() = board?.canRedo ?: false

    override fun undo() {
        board?.undo()
    }

    override fun redo() {
        board?.redo()
    }

    //endregion

    private companion object {
        const val RULE_CONFLICT = "Queens can't touch or share a row, column, or color."
    }
}
