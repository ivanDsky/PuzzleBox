package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
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
import com.ivandsky.puzzlebox.games.tangoui.hint.tangoStepHints
import java.time.Instant

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
    internal val hintMode: HintMode = HintMode.Legacy,
) : GamePlayController, PlayClock, PlayHints, PlayUndo, PlayFeedback, PlayGuide {

    var session by mutableStateOf<TangoSession?>(null)
        private set
    val hintCell: Int? get() = legacyHint.cell

    /** The last hint's WHY (§6 hint caption — the reveal names the deduction class). */
    val hintWhy: TangoHint.Why? get() = legacyHint.why
    var lastRejection by mutableStateOf<TangoVerdict.Rejected?>(null)
        private set

    override val isComplete: Boolean get() = session?.isComplete == true

    private val rules = TangoRules
    internal val guidance = TangoGuide()

    private val clock = TangoRunClock(now)
    private val settle = TangoSettle(env.scope)
    private val legacyHint = TangoLegacyHint()

    // HintParity (A1, A9, A10): built per play; null in Legacy mode, so the legacy path below runs unchanged.
    private var stepHints by mutableStateOf((hintMode as? HintMode.Stepwise)?.let(::newStepHints))

    /** The Stepwise hint on screen; null in Legacy mode and after any input. */
    val spotlight: Spotlight? get() = stepHints?.spotlight

    //region input (placements land; violations are state-driven, §6)

    val suppressedSets: Set<Set<Int>> get() = settle.suppressed

    /**
     * A board tap on [idx]: the [TangoTapPolicy] cycle step read off the LIVE session, so a
     * second tap before the board recomposes still advances sun → moon (M4-tango-28 — a
     * step computed from the last composed model repeated the first and no-opped).
     */
    fun onTap(idx: Int) {
        val model = rendererModel() ?: return
        TangoTapPolicy.onTap(model, idx).forEach(::onPlacement)
    }

    fun onPlacement(placement: TangoPlacement) {
        lastRejection = null // next input clears the violation verdict (Zip hintArrow discipline)
        legacyHint.clear()
        stepHints?.inputMade()
        settle.arm()
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
        settle.suppress(violatingSets(current.puzzle, next.grid()) - before)
        updateSession(if (next.isComplete) clock.fold(next) else next)
        if (mistake != null) lastRejection = TangoVerdict.Rejected(mistake, move.idx)
    }

    /**
     * Undo/redo/clear is a board change like a placement, so it follows the same input rules:
     * transient reveals clear, and violations it NEWLY creates (a redo can restore one) wait
     * out the settle window while ones it removes vanish at once. The clock is not rewound.
     */
    private fun stepHistory(step: (TangoSession) -> TangoSession) {
        val current = session ?: return
        stepHints?.inputMade() // any history press is input, even one with nothing to move (A10)
        val next = step(current)
        if (next == current) return // nothing to move, or the session is complete
        lastRejection = null
        settle.arm()
        settle.suppress(violatingSets(current.puzzle, next.grid()) - violatingSets(current.puzzle, current.grid()))
        commit(next)
    }

    override val canUndo: Boolean get() = session?.let { !it.isComplete && it.history.canUndo } ?: false
    override val canRedo: Boolean get() = session?.let { !it.isComplete && it.history.canRedo } ?: false

    override fun undo() = stepHistory(TangoSession::withUndo)

    override fun redo() = stepHistory(TangoSession::withRedo)

    /** Clear: every player placement in ONE undo unit, givens stay; a no-op on an empty or solved board. */
    fun clearBoard() = stepHistory(TangoSession::withCleared)

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
        clock.start() // the clock runs from the moment the puzzle opens
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
                // The running clock is process-local (≠ session state): a restored in-progress
                // session restarts its window now, or the timer reads frozen forever (user QA 2026-11-02).
                if (s.isComplete) clock.stop() else clock.start()
                Restored(s.ref)
            }

            is DecodeResult.Err -> null
        }

    //endregion

    /** Hint (spec hints bullet, Zip parity): the ONE counted hint; [TangoLegacyHint] auto-places the forced move. */
    override fun requestHint() {
        val current = session ?: return
        if (current.isComplete) return
        clock.resume() // a hint that places is play
        stepHints?.let { it.press(); return } // Stepwise never places (A1)
        legacyHint.resolve(current, now(), ::commit)
    }

    private fun newStepHints(mode: HintMode.Stepwise) = tangoStepHints(mode, env.scope, { session }, count = ::updateSession)

    //region timer (F2 §2: active foreground play only)

    override fun tick() = clock.tick()

    override fun onBackground() {
        val current = session ?: return
        if (clock.since != null) updateSession(clock.fold(current))
    }

    override fun onForeground() {
        val current = session ?: return
        if (!current.isComplete) clock.resume()
    }

    /** The running window's identity — the loop restarts each time it (re)opens. */
    override val clockWindow: Any? get() = clock.since

    override fun displayedElapsedMs(): Long = session?.let { clock.displayedElapsedMs(it.elapsedMs) } ?: 0L

    //endregion

    //region PlayHints + PlayFeedback + PlayGuide
    override val hintsUsed: Int get() = session?.hintsUsed ?: 0
    override val hintEnabled: Boolean get() = session?.isComplete == false
    // While a Spotlight shows its callout carries the text: one message on screen (A11).
    override val hintCaption: String? get() = if (spotlight != null) null else session?.let(legacyHint::caption)
    /** §6 state-driven live-grid violation (outranks [hintCaption]); placements LAND, so no [rejection]. */
    override val violation: String? get() =
        if (spotlight != null) null else rendererModel()?.let(TangoSemantics::firstViolationPhrase)
    override val rejection: String? get() = null
    override fun guide() { session?.puzzle?.let(guidance::start) }
    //endregion

    /** Result's "Play again": a fresh session of the same puzzle, every transient reveal
     *  cleared and a fresh clock window open — the next win is a new transition and emits again. */
    override fun replay() {
        val current = session ?: return
        legacyHint.clear()
        lastRejection = null
        clock.start()
        commit(TangoSession(ref = current.ref, puzzle = current.puzzle)) // drops the old play's search
        stepHints = (hintMode as? HintMode.Stepwise)?.let(::newStepHints) // a fresh play forgets counted steps
    }

    //region derived + persistence

    /** The renderer model the board draws — built here (F2: the state→model call site). */
    fun rendererModel(): TangoRendererModel? {
        val current = session ?: return null
        // The hint rides the renderer model for the cell highlight (§7 HintReveal).
        return TangoRendererModel.of(
            TangoState(current.puzzle, current.grid()),
            hint = legacyHint.forced(current),
            verdict = lastRejection,
            suppressedSets = suppressedSets,
        ).copy(hintWhy = null).let(guidance::apply)
    }

    /** Versioned session bytes (A19/F2 §2) — what [PlayEnvironment.onSessionChanged] receives. */
    fun persist(): ByteArray? = session?.let(TangoSessionCodec::serialize)

    //endregion

    //region internals

    private fun commit(next: TangoSession) {
        legacyHint.clear()
        stepHints?.inputMade()
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
