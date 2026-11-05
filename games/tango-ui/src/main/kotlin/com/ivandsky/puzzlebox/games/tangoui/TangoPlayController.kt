package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.MotionTokens
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
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Tango play controller (tango-05): one adopted board → engine → win transition,
 * mirroring [com.ivandsky.puzzlebox.games.zipui.ZipPlayController]'s shape. Owns the
 * [TangoSession] as the single source of truth for placements/hints/timer/undo history;
 * every committed change round-trips through [PlayEnvironment.onSessionChanged] so the
 * play feature persists bytes across process death and rotation (A19, TangoSessionCodec).
 *
 * Placement policy (design §6, LinkedIn-faithful): a placement that would VIOLATE a
 * rule still LANDS on the board — the violation is state-driven and persists until the
 * board is legal again, with the rule-naming caption announced once per new violation.
 * Only inert placements (givens, no-ops, past completion) are refused. A violating
 * placement counts one mistake (analytics, never scored — A3) and can never complete
 * the board ([TangoRules.isComplete] demands every constraint satisfied).
 *
 * A15 gate: a daily that decodes but is not uniquely solvable is never playable —
 * [adoptToday] rejects it with the payload-untrusted copy, never guessed through.
 *
 * Win: the [TangoSession.completedAt] transition hands ONE completion record to
 * [PlayEnvironment.onWon]; the play feature writes it and derives the A4 badge.
 */
class TangoPlayController(
    private val env: PlayEnvironment,
    private val now: () -> Instant = Instant::now,
) : GamePlayController, PlayClock, PlayHints, PlayUndo, PlayFeedback, PlayGuide {

    var session by mutableStateOf<TangoSession?>(null)
        private set
    var hintCell by mutableStateOf<Int?>(null)
        private set

    /** The last hint's WHY (§6 hint caption — the reveal names the deduction class). */
    var hintWhy by mutableStateOf<TangoHint.Why?>(null)
        private set
    var lastRejection by mutableStateOf<TangoVerdict.Rejected?>(null)
        private set
    var nowMs by mutableStateOf(0L)
        private set

    override val isComplete: Boolean get() = session?.isComplete == true

    private val rules = TangoRules
    internal val guidance = TangoGuide()

    /** The running timer window; folds into [TangoSession.elapsedMs] on pause/background.
     *  Compose state on purpose: the clock loop keys off [clockWindow], so every change
     *  here must be observable — the timer lives and dies by reactivity. */
    private var runningSince: Instant? by mutableStateOf(null)

    //region input (placements land; violations are state-driven, §6)

    // Settle debounce, diff-based (user QA 2026-11-02): ONLY violations newly introduced
    // by the last placement are suppressed for 0.5 s. Violations that already existed
    // stay on screen until the grid change actually fixes them — an unfixed error never
    // disappears because the user touched elsewhere.
    var suppressedSets by mutableStateOf<Set<Set<Int>>>(emptySet())
        private set
    private var settleJob: Job? = null

    /** The violating cell-sets of a grid, via the shared scan (no suppression applied). */
    private fun violatingSets(puzzle: TangoPuzzle, grid: List<TangoCell?>): Set<Set<Int>> =
        TangoSemantics.violations(TangoRendererModel.of(state = TangoState(puzzle, grid), hint = null))
            .mapTo(mutableSetOf()) { it.cells }

    fun onPlacement(placement: TangoPlacement) {
        lastRejection = null // next input clears the violation verdict (Zip hintArrow discipline)
        hintCell = null
        hintWhy = null
        armSettle()
        val current = session ?: return
        if (current.isComplete) return
        val puzzle = current.puzzle
        val grid = current.grid()
        if (grid[placement.idx] == placement.value) return // no-op
        val before = violatingSets(puzzle, grid)
        val move = TangoMove(placement.idx, placement.value)
        when (val verdict = rules.validate(move, TangoState(puzzle, grid))) {
            is TangoVerdict.Valid -> land(current, move, placedAt = now(), mistake = null, before = before)

            // Inert by policy (the tap policy never emits given/foreign placements).
            is TangoVerdict.Rejected if verdict.reason == TangoVerdict.Reason.LockedGiven ||
                verdict.reason == TangoVerdict.Reason.ForeignMove -> Unit

            // A rule-violating placement LANDS (§6): mistake + rule-naming caption, none while guided.
            is TangoVerdict.Rejected -> land(current, move, now(), verdict.reason.takeUnless { guidance.active }, before)
        }
    }

    /** Commits [move], counting the optional violation, and stamps a first win. */
    private fun land(
        current: TangoSession,
        move: TangoMove,
        placedAt: Instant,
        mistake: TangoVerdict.Reason?,
        before: Set<Set<Int>>,
    ) {
        val moved = current.withMove(move, placedAt)
        val next = if (mistake != null) moved.withRejection() else moved
        if (next == current) return // nothing changed (e.g. a hinted-equal placement)
        // Suppress only the NEW violations this move introduced (after − before).
        suppressedSets = violatingSets(current.puzzle, next.grid()) - before
        if (runningSince == null && !next.isComplete) runningSince = placedAt
        updateSession(if (next.isComplete) foldRunning(next) else next)
        if (mistake != null) lastRejection = TangoVerdict.Rejected(mistake, move.idx)
    }

    /**
     * Undo/redo is a board change like a placement, so it follows the same input rules:
     * transient reveals clear, and violations it NEWLY creates (a redo can restore one) wait
     * out the settle window while ones it removes vanish at once. The clock is not rewound;
     * a stopped one reopens, since undoing/redoing is play.
     */
    private fun stepHistory(step: (TangoSession) -> TangoSession) {
        val current = session ?: return
        val next = step(current)
        if (next == current) return // nothing to move, or the session is complete
        lastRejection = null
        armSettle()
        suppressedSets = violatingSets(current.puzzle, next.grid()) - violatingSets(current.puzzle, current.grid())
        if (runningSince == null) runningSince = now()
        commit(next)
    }

    private fun armSettle() {
        settleJob?.cancel()
        settleJob = env.scope.launch {
            delay(MotionTokens.SettleMillis) // (re)started window after which new violations show
            suppressedSets = emptySet()
        }
    }

    override val canUndo: Boolean get() = session?.let { !it.isComplete && it.history.canUndo } ?: false
    override val canRedo: Boolean get() = session?.let { !it.isComplete && it.history.canRedo } ?: false

    override fun undo() = stepHistory(TangoSession::withUndo)

    override fun redo() = stepHistory(TangoSession::withRedo)

    //endregion

    //region adopt / restore

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        val tango = when (val decoded = TangoCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return Adopt.Rejected(BROKEN_PUZZLE_MESSAGE)
        }
        // A15: an ambiguous puzzle is never playable — the untrusted unavailable state.
        if (!TangoUniqueness.isUniquelySolvable(tango)) return Adopt.Rejected(BROKEN_PUZZLE_MESSAGE)
        // Round-trip the initial session through the callback too: the route's saveable
        // must exist from load, not first placement (C1 process-death window).
        updateSession(TangoSession(ref = ref as TangoPuzzleRef, puzzle = tango))
        return Adopt.Adopted
    }

    /** Rotation/process-death + history-replay restore (one path, Zip parity): a
     *  restored COMPLETED session shows the win without re-emitting (emission happens
     *  on the transition only). */
    override fun adoptRestored(bytes: ByteArray): Restored? =
        when (val restored = TangoSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value
                session = s
                // The running clock is process-local (runningSince ≠ session state): a
                // restored in-progress session restarts its window now, or the timer
                // reads 0 forever (user QA 2026-11-02).
                if (!s.isComplete && hasPlayed(s)) runningSince = now()
                Restored(s.ref)
            }

            is DecodeResult.Err -> null
        }

    //endregion

    /**
     * Hint (spec hints bullet, Zip parity): the ONE counted hint AUTO-PLACES the forced move as an ordinary,
     * undoable [TangoPlace], highlights it and captions its WHY until the next input. Undo refunds nothing.
     */
    override fun requestHint() {
        val current = session ?: return
        if (current.isComplete) return
        if (runningSince == null) runningSince = now() // a hint that places is play
        when (val resolved = rules.hint(TangoState(current.puzzle, current.grid()))) {
            is TangoHint.Forced -> {
                val move = TangoMove(resolved.idx, resolved.value)
                if (rules.validate(move, TangoState(current.puzzle, current.grid())) is TangoVerdict.Valid) {
                    commit(current.withMove(move, now()).withHintApplied(resolved))
                } else {
                    commit(current.withHintApplied(resolved))
                }
                hintCell = resolved.idx
                hintWhy = resolved.why
            }

            // No forced move exists — the ladder answer costs the player nothing (I7).
            TangoHint.Stuck -> commit(current.withHintApplied(resolved))
        }
    }

    //region timer (F2 §2: active foreground play only)

    override fun tick() {
        nowMs = now().toEpochMilli()
    }

    override fun onBackground() {
        val current = session ?: return
        if (runningSince != null) updateSession(foldRunning(current))
    }

    override fun onForeground() {
        val current = session ?: return
        if (runningSince == null && !current.isComplete && hasPlayed(current)) {
            runningSince = now()
        }
    }

    /** The running window's identity — the loop restarts each time it (re)opens. */
    override val clockWindow: Any?
        get() = runningSince

    /** What TimerBar draws right now (banked + running window). Never negative. */
    override fun displayedElapsedMs(): Long {
        val current = session ?: return 0L
        val window = runningSince?.let { Duration.between(it, Instant.ofEpochMilli(nowMs)).toMillis() } ?: 0L
        return (current.elapsedMs + window).coerceAtLeast(0L)
    }

    /** Folds the running window into the session's banked elapsed and stops the clock. */
    private fun foldRunning(target: TangoSession): TangoSession {
        val rs = runningSince ?: return target
        runningSince = null
        return target.copy(elapsedMs = target.elapsedMs + Duration.between(rs, now()).toMillis())
    }

    //endregion

    /** Whether this session has been played — an emptied board with history still counts. */
    private fun hasPlayed(s: TangoSession): Boolean =
        s.placements.isNotEmpty() || s.history.canUndo || s.history.canRedo

    //region PlayHints + PlayFeedback + PlayGuide
    override val hintsUsed: Int get() = session?.hintsUsed ?: 0
    override val hintEnabled: Boolean get() = session?.isComplete == false
    /** The hint's WHY sentence (§6), shown by the shared chrome until the next input. */
    override val hintCaption: String?
        get() {
            val why = hintWhy ?: return null
            val current = session ?: return null
            return TangoSemantics.hintPhrase(
                TangoRendererModel.of(TangoState(current.puzzle, current.grid()), hint = null),
                why,
            )
        }
    /** §6 state-driven live-grid violation (outranks [hintCaption]); placements LAND, so no [rejection]. */
    override val violation: String? get() = rendererModel()?.let(TangoSemantics::firstViolationPhrase)
    override val rejection: String? get() = null
    override fun guide() { session?.puzzle?.let(guidance::start) }
    //endregion

    /** Result's "Play again": a fresh session of the same puzzle, every transient reveal
     *  cleared and the clock stopped — the next win is a new transition and emits again. */
    override fun replay() {
        val current = session ?: return
        hintCell = null
        hintWhy = null
        lastRejection = null
        runningSince = null
        commit(TangoSession(ref = current.ref, puzzle = current.puzzle))
    }

    //region derived + persistence

    /** The renderer model the board draws — built here (F2: the state→model call site). */
    fun rendererModel(): TangoRendererModel? {
        val current = session ?: return null
        // The hint rides the renderer model for the cell highlight (§7 HintReveal); its WHY
        // sentence is shown once, by the shared chrome's [hintCaption], not the board's slot.
        val hint = hintCell?.let { idx ->
            hintWhy?.let { why -> current.grid()[idx]?.let { value -> TangoHint.Forced(idx, value, why) } }
        }
        return TangoRendererModel.of(
            TangoState(current.puzzle, current.grid()),
            hint = hint,
            verdict = lastRejection,
            suppressedSets = suppressedSets,
        ).copy(hintWhy = null).let(guidance::apply)
    }

    /** Versioned session bytes (A19/F2 §2) — what [PlayEnvironment.onSessionChanged] receives. */
    fun persist(): ByteArray? = session?.let(TangoSessionCodec::serialize)

    //endregion

    //region internals

    private fun commit(next: TangoSession) {
        hintCell = null
        hintWhy = null
        updateSession(next)
    }

    private fun updateSession(next: TangoSession) {
        val won = next.isComplete && session?.isComplete != true
        session = next
        env.onSessionChanged(TangoSessionCodec.serialize(next))
        if (won) env.onWon(tangoPlugin.score.completion(next) ?: return)
    }

    //endregion
}
