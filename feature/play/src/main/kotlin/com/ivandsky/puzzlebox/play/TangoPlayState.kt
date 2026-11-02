package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.TangoToday
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
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
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The Tango play screen's async board states — the [ZipScreen] shape applied to Tango. */
sealed interface TangoScreen {
    data object Loading : TangoScreen

    /** Nothing playable today; [message] drives the error copy. Retry re-runs the fetch. */
    data class Unavailable(val message: String) : TangoScreen

    /** Board in hand. [degraded] non-null = playing the SAVED puzzle because the fetch
     *  failed — surfaced as a banner, the board stays playable (A7/A8). */
    data class Ready(val puzzle: TangoPuzzle, val ref: TangoPuzzleRef?, val degraded: FetchError?) : TangoScreen
}

/**
 * The Tango play state holder (tango-05): today's board → engine → win flow, mirroring
 * [ZipPlayState]. Owns the [TangoSession] as the single source of truth for placements/
 * hints/timer; every committed change round-trips through [onSessionChanged] so the
 * route persists bytes across process death and rotation (A19, TangoSessionCodec).
 *
 * Placement policy (design §6, LinkedIn-faithful): a placement that would VIOLATE a
 * rule still LANDS on the board — the violation is state-driven and persists until the
 * board is legal again, with the rule-naming caption announced once per new violation.
 * Only inert placements (givens, no-ops, past completion) are refused. A violating
 * placement counts one mistake (analytics, never scored — A3) and can never complete
 * the board ([TangoRules.isComplete] demands every constraint satisfied).
 *
 * A15 gate: a daily that decodes but is not uniquely solvable is never playable —
 * the payload-untrusted unavailable state, never guessed through.
 *
 * Win flow: the [TangoSession.completedAt] transition emits ONE [CompletionRecord]
 * via [CompletionRecordEmitter] — fire-and-observe, never blocking the route handoff.
 * [recordBadge] is null while the write is in flight, then the A4 verdict for THIS play.
 */
class TangoPlayState(
    private val daily: TangoDailyRepository,
    private val completions: CompletionRepository,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
    private val today: () -> LocalDate = { LocalDate.now(tangoCadenceZone()) },
    private val onSessionChanged: (TangoSession?) -> Unit = {},
) : ActivePlay, PlayClock {

    override val game: PlayGame get() = PlayGame.Tango

    var screen by mutableStateOf<TangoScreen>(TangoScreen.Loading)
        private set
    var session by mutableStateOf<TangoSession?>(null)
        private set
    var hintCell by mutableStateOf<Int?>(null)
        private set

    /** The last hint's WHY (§6 hint caption — the reveal names the deduction class). */
    var hintWhy by mutableStateOf<TangoHint.Why?>(null)
        private set
    var rejection by mutableStateOf<TangoVerdict.Rejected?>(null)
        private set
    override var recordBadge by mutableStateOf<Boolean?>(null)
        private set
    override var recordWriteFailed by mutableStateOf(false)
        private set
    var nowMs by mutableStateOf(0L)
        private set

    /** A fresh entry must fetch: no live session, or the live one already finished. */
    override val needsLoad: Boolean
        get() = session == null || session?.isComplete == true

    private val rules = TangoRules
    private var emitter: CompletionRecordEmitter? = null

    /** The running timer window; folds into [TangoSession.elapsedMs] on pause/background. */
    private var runningSince: Instant? = null

    /** Whether a record already existed for the current ref BEFORE this session's win —
     *  the A4 badge is derived from this baseline, never from the stored rows. */
    private var baselineHadRecord: Boolean? = null

    //region input (placements land; violations are state-driven, §6)

    fun onPlacement(placement: TangoPlacement) {
        rejection = null // next input clears the violation caption (Zip hintArrow discipline)
        hintCell = null
        hintWhy = null
        val current = session ?: return
        val ready = screen as? TangoScreen.Ready ?: return
        if (current.isComplete) return
        val grid = current.grid()
        if (grid[placement.idx] == placement.value) return // no-op
        val move = TangoMove(placement.idx, placement.value)
        when (val verdict = rules.validate(move, TangoState(ready.puzzle, grid))) {
            is TangoVerdict.Valid -> land(current, move, placedAt = now(), mistake = null)

            // Inert by policy (the tap policy never emits given/foreign placements).
            is TangoVerdict.Rejected if verdict.reason == TangoVerdict.Reason.LockedGiven ||
                verdict.reason == TangoVerdict.Reason.ForeignMove -> Unit

            // A rule-violating placement LANDS (§6): mistake + rule-naming caption.
            is TangoVerdict.Rejected -> land(current, move, placedAt = now(), mistake = verdict.reason)
        }
    }

    /** Commits [move], counting the optional violation, and stamps a first win. */
    private fun land(current: TangoSession, move: TangoMove, placedAt: Instant, mistake: TangoVerdict.Reason?) {
        val moved = current.withMove(move, placedAt)
        val next = if (mistake != null) moved.withRejection() else moved
        if (next == current) return // nothing changed (e.g. a hinted-equal placement)
        if (runningSince == null && !next.isComplete) runningSince = placedAt
        updateSession(if (next.isComplete) foldRunning(next) else next)
        if (mistake != null) rejection = TangoVerdict.Rejected(mistake, move.idx)
    }

    //endregion

    //region load / restore

    override fun load() {
        resetTransient()
        scope.launch {
            when (val t = daily.today()) {
                is TangoToday.Fresh -> adopt(t.puzzle, degraded = null)
                is TangoToday.Cached -> adopt(t.puzzle, degraded = t.error)
                is TangoToday.Unavailable -> screen = TangoScreen.Unavailable(unavailableMessage(t.error))
            }
        }
    }

    private suspend fun adopt(puzzle: Puzzle, degraded: FetchError?) {
        val tango = when (val decoded = TangoCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> {
                screen = TangoScreen.Unavailable(UNTRUSTED_COPY)
                return
            }
        }
        // A15: an ambiguous puzzle is never playable — the untrusted unavailable state.
        if (!TangoUniqueness.isUniquelySolvable(tango)) {
            screen = TangoScreen.Unavailable(UNTRUSTED_COPY)
            return
        }
        val ref = resolveRef()
        screen = TangoScreen.Ready(tango, ref, degraded)
        session = TangoSession(ref = ref, puzzle = tango)
        refreshBaseline(ref)
    }

    /** Rotation/process-death + history-replay restore (one path, Zip parity): a
     *  corrupt save falls back to a normal load; a restored COMPLETED session shows
     *  the win without re-emitting (emission happens on the transition only). */
    override fun adoptRestored(bytes: ByteArray) {
        when (val restored = TangoSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value
                emitter = null
                session = s
                screen = TangoScreen.Ready(s.puzzle, s.ref, degraded = null)
                if (!s.isComplete) refreshBaseline(s.ref)
            }

            is DecodeResult.Err -> load()
        }
    }

    /** Record identity = gameId tango + day/puzzleNumber (ADR-0004): the repository
     *  persists both at fetch time and history rows carry them; today's row (in Tango's
     *  cadence zone) is the identity of the board we just loaded. */
    private suspend fun resolveRef(): TangoPuzzleRef? {
        val day = today()
        val entry = daily.observeHistory().first().firstOrNull { it.day == day }
        return TangoPuzzleRef(entry?.day ?: day, entry?.puzzleNumber)
    }

    private fun refreshBaseline(ref: TangoPuzzleRef?) {
        if (ref == null) return
        scope.launch {
            baselineHadRecord = completions.observeRecords().first().any { it.puzzleRef == ref }
        }
    }

    //endregion

    /**
     * Hint (spec hints bullet, Zip parity): the ONE counted hint AUTO-PLACES the
     * forced move and keeps its WHY for the caption until the next input. The board
     * highlight is 04's known gap (renderer model carries no hint payload) — the
     * reveal is the auto-placement itself plus the side-panel caption.
     */
    fun hint() {
        val current = session ?: return
        val ready = screen as? TangoScreen.Ready ?: return
        if (current.isComplete) return
        if (runningSince == null) runningSince = now() // a hint that places is play
        when (val resolved = rules.hint(TangoState(ready.puzzle, current.grid()))) {
            is TangoHint.Forced -> {
                val move = TangoMove(resolved.idx, resolved.value)
                if (rules.validate(move, TangoState(ready.puzzle, current.grid())) is TangoVerdict.Valid) {
                    commit(current.withMove(move, now()).withHintApplied())
                } else {
                    commit(current.withHintApplied())
                }
                hintCell = resolved.idx
                hintWhy = resolved.why
            }

            // No forced move exists on this grid — the hint is still counted (Zip parity).
            TangoHint.Stuck -> commit(current.withHintApplied())
        }
    }

    //endregion

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
        if (runningSince == null && !current.isComplete && current.placements.isNotEmpty()) {
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

    //region side panel copy (§4: "Daily · #NNN" / "Replay · MMM d")

    /** An archived-date session (ref date ≠ today in Tango's cadence zone) is a replay. */
    val subtitle: String
        get() {
            val ref = session?.ref ?: return "Daily"
            return when {
                ref.date != today() -> "Replay · ${ref.date.format(REPLAY_DATE)}"
                else -> ref.puzzleNumber?.let { "Daily · #$it" } ?: "Daily"
            }
        }

    //endregion

    //region win flow

    /** Result's "Play again": a fresh session, a new emitter lifetime — replays emit
     *  again (A4), and the badge resets until the new write lands. The A4 baseline
     *  is re-read: whether a record existed BEFORE this new play. */
    override fun replay() {
        val ready = screen as? TangoScreen.Ready ?: return
        emitter = null
        recordBadge = null
        recordWriteFailed = false
        hintCell = null
        hintWhy = null
        rejection = null
        baselineHadRecord = null
        runningSince = null
        refreshBaseline(ready.ref)
        commit(TangoSession(ref = ready.ref, puzzle = ready.puzzle))
    }

    override fun retryRecordWrite() {
        val completed = session?.takeIf { it.isComplete } ?: return
        emitRecord(record = tangoPlugin.score.completion(completed) ?: return)
    }

    private fun onWin(completed: TangoSession) {
        emitRecord(record = tangoPlugin.score.completion(completed) ?: return)
    }

    private fun emitRecord(record: CompletionRecord) {
        recordWriteFailed = false
        recordBadge = null
        val instance = CompletionRecordEmitter(scope) { r ->
            completions.onCompleted(r)
            AppResult.Ok(Unit)
        }
        emitter = instance
        instance.emit(record) { result ->
            when (result) {
                // A4 badge for THIS play: true only when no record existed at session
                // start; unknown baseline (write raced the baseline read) stays null.
                is AppResult.Ok -> recordBadge = baselineHadRecord?.let { !it }
                is AppResult.Err -> recordWriteFailed = true
            }
        }
    }

    //endregion

    //region derived + persistence

    /** The renderer model the board draws — built here (F2: the state→model call site). */
    fun rendererModel(): TangoRendererModel? {
        val current = session ?: return null
        val ready = screen as? TangoScreen.Ready ?: return null
        return TangoRendererModel.of(TangoState(ready.puzzle, current.grid()), hint = null, verdict = rejection)
    }

    /** Versioned session bytes for the route's rememberSaveable (A19/F2 §2). */
    fun persist(): ByteArray? = session?.let(TangoSessionCodec::serialize)

    //endregion

    //region internals

    private fun resetTransient() {
        screen = TangoScreen.Loading
        session = null
        hintCell = null
        hintWhy = null
        rejection = null
        recordBadge = null
        recordWriteFailed = false
        baselineHadRecord = null
        emitter = null
    }

    private fun commit(next: TangoSession) {
        hintCell = null
        hintWhy = null
        updateSession(next)
    }

    private fun updateSession(next: TangoSession) {
        val won = next.isComplete && session?.isComplete != true
        session = next
        onSessionChanged(next)
        if (won) onWin(next)
    }

    //endregion

    private companion object {
        /** §6 Unavailable (payload untrusted, A15/A16) — the same copy both games share. */
        val UNTRUSTED_COPY = "Today's puzzle failed verification, so it can't be played. Try again later."

        val REPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

        /** A10: Tango rolls in LinkedIn's zone; TangoDefinition pins America/Los_Angeles. */
        fun tangoCadenceZone() = (TangoDefinition.cadence as Cadence.Daily).zone
    }
}
