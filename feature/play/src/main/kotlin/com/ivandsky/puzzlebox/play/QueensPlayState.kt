package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.QueensDailyRepository
import com.ivandsky.puzzlebox.data.repository.QueensToday
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayHints
import com.ivandsky.puzzlebox.gameui.PlayUndo
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensBoardState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The Queens play screen's async board states (zip ZipScreen precedent, A7/A8). */
sealed interface QueensScreen {
    data object Loading : QueensScreen

    /** Nothing playable today; [message] drives the error copy. Retry re-runs the fetch. */
    data class Unavailable(val message: String) : QueensScreen

    /** Board in hand; [degraded] non-null = playing the saved puzzle because the fetch
     *  failed — surfaced as a banner, the board stays playable. */
    data class Ready(val puzzle: com.ivandsky.puzzlebox.games.queens.QueensPuzzle, val ref: QueensPuzzleRef?, val degraded: FetchError?) : QueensScreen
}

/**
 * The Queens play state holder (queens-10 wiring; zip [ZipPlayState] shape): today's
 * board (queens-06 offline-first repository) → a real [QueensSession] (queens-04) →
 * the queens-08 [QueensBoardState] interaction holder. The holder owns only the fetch /
 * restore / record / clock wiring — every game decision stays in the session and the
 * board state (A10–A12 provenance, A14 Auto-cross, §5 interaction set, command undo).
 *
 * Win flow (fix round 6 — zip's win→result-route pattern): the board state hands ONE
 * [CompletionRecord] (built by the plugin's score model on the completedAt transition)
 * to the record emitter; the write stays fire-and-observe in the flow scope —
 * navigation (to the result route) can never cancel it. [recordBadge] is
 * null while the write is in flight, then the A4 verdict against the session-start
 * baseline; [recordWriteFailed] surfaces the retry affordance on the result route.
 * A restored completed session never re-emits (transition-only) and refreshes no
 * baseline.
 *
 * Timer (Tango parity): the session banks [QueensSession.elapsedMs]; this state owns the
 * process-local running window, so undo/redo never touch it.
 */
class QueensPlayState(
    private val daily: QueensDailyRepository,
    private val completions: CompletionRepository,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
    private val today: () -> LocalDate = { LocalDate.now(queensCadenceZone) },
    /**
     * The entry's session-persistence callback (R-4): the play route serializes every
     * committed session into its `rememberSaveable`; it also receives the initial session
     * of a fresh load/replay so a process death before the first move still restores.
     */
    private val onSessionChanged: (QueensSession?) -> Unit = {},
) : ActivePlay, PlayClock, PlayHints, PlayUndo {

    override val game: PlayGame get() = PlayGame.Queens

    var screen by mutableStateOf<QueensScreen>(QueensScreen.Loading)
        private set

    /** The live interaction holder, null until a session is adopted. */
    var board by mutableStateOf<QueensBoardState?>(null)
        private set

    /** A fresh entry must fetch: no live board, or the live one already finished. */
    override val needsLoad: Boolean
        get() = board?.session?.isComplete != false

    /** A4 record badge: null = write in flight (or baseline unknown); true/false = the
     *  verdict against the session-start baseline (new record vs beat replay). */
    override var recordBadge by mutableStateOf<Boolean?>(null)
        private set

    /** The record write failed — the result route offers a retry. */
    override var recordWriteFailed by mutableStateOf(false)
        private set

    private var emitter: CompletionRecordEmitter? = null

    /** The open running window's start: null = clock stopped (not started, backgrounded, or won). */
    private var runningSince: Instant? by mutableStateOf(null)

    /** Last [tick]; the window's displayed length is measured against it so the timer recomposes. */
    var nowMs by mutableStateOf(0L)
        private set

    /** Whether a record already existed for the current ref BEFORE this session's win —
     *  the A4 badge is derived from this baseline, never from the stored rows. */
    private var baselineHadRecord: Boolean? = null

    //region load / restore

    /** Fetch-today → board (A7/A8): Fresh plays; Cached plays with a degrade banner;
     *  Unavailable is the error state; an untrustable payload is never playable (A16). */
    override fun load() {
        screen = QueensScreen.Loading
        board = null
        emitter = null
        recordBadge = null
        recordWriteFailed = false
        baselineHadRecord = null
        runningSince = null
        scope.launch {
            when (val today = daily.today()) {
                is QueensToday.Fresh -> adopt(today.puzzle, degraded = null)
                is QueensToday.Cached -> adopt(today.puzzle, degraded = today.error)
                is QueensToday.Unavailable -> screen = QueensScreen.Unavailable(unavailableMessage(today.error))
            }
        }
    }

    /** Process-death/rotation restore: the codec carries the puzzle BY VALUE, so a
     *  corrupt save falls back to a normal load; a restored COMPLETED session shows the
     *  result without re-emitting (emission happens on the transition only). */
    override fun adoptRestored(bytes: ByteArray) {
        when (val restored = QueensSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value
                emitter = null
                recordBadge = null
                recordWriteFailed = false
                baselineHadRecord = null
                runningSince = null
                board = boardFor(s)
                screen = QueensScreen.Ready(s.puzzle, s.ref, degraded = null)
                // The running clock is process-local (runningSince is not session state):
                // a restored in-progress session restarts its window now, or the timer
                // reads frozen forever (Tango parity, user QA 2026-11-02).
                if (!s.isComplete && s.hasStarted) openWindow()
                if (!s.isComplete) refreshBaseline(s.ref)
            }

            is DecodeResult.Err -> load()
        }
    }

    private suspend fun adopt(puzzle: Puzzle, degraded: FetchError?) {
        val queens = when (val decoded = QueensCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> {
                screen = QueensScreen.Unavailable(untrustedMessage(decoded.error))
                return
            }
        }
        val ref = resolveRef()
        screen = QueensScreen.Ready(queens, ref, degraded)
        val fresh = QueensSession(ref = ref, puzzle = queens)
        board = boardFor(fresh)
        onSessionChanged(fresh)
        refreshBaseline(ref)
    }

    /** Record identity = gameId queens + day/puzzleNumber (ADR-0004), resolved the same
     *  way Zip's play resolves its ref: today's cached row IS the board we just loaded. */
    private suspend fun resolveRef(): QueensPuzzleRef? {
        val day = today()
        val entry = daily.observeHistory().first().firstOrNull { it.day == day }
        return QueensPuzzleRef(entry?.day ?: day, entry?.puzzleNumber)
    }

    private fun refreshBaseline(ref: QueensPuzzleRef?) {
        if (ref == null) return
        scope.launch {
            baselineHadRecord = completions.observeRecords().first().any { it.puzzleRef == ref }
        }
    }

    private fun boardFor(session: QueensSession): QueensBoardState = QueensBoardState(
        initialSession = session,
        score = queensPlugin.score,
        onWinRecord = { emitRecord(it) },
        now = now,
        onSessionChanged = { onSessionChanged(it) },
        settle = this::settle,
    )

    /** Result's "Play again" parity (zip replay): a fresh session, a new emitter lifetime —
     *  replays emit again (A4), and the badge resets until the new write lands. The A4
     *  baseline is re-read: whether a record existed BEFORE this new play. */
    override fun replay() {
        val ready = screen as? QueensScreen.Ready ?: return
        emitter = null
        recordBadge = null
        recordWriteFailed = false
        baselineHadRecord = null
        runningSince = null
        refreshBaseline(ready.ref)
        val fresh = QueensSession(ref = ready.ref, puzzle = ready.puzzle)
        board = boardFor(fresh)
        onSessionChanged(fresh)
    }

    override fun retryRecordWrite() {
        val completed = board?.session?.takeIf { it.isComplete } ?: return
        emitRecord(record = queensPlugin.score.completion(completed) ?: return)
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

    //region PlayClock (F2 §2: active foreground play only)

    /**
     * The board's hook on every committed session, BEFORE it is stored and the win record
     * built: the first committed action (or counted hint) opens the running window, and
     * the win folds it into the banked [QueensSession.elapsedMs] so the frozen result and
     * the record carry the real duration.
     */
    private fun settle(next: QueensSession): QueensSession {
        if (next.isComplete) return foldRunning(next)
        if (runningSince == null) openWindow()
        return next
    }

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
        if (runningSince == null && !current.isComplete && current.hasStarted) openWindow()
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

    //region PlayHints / PlayUndo / result copy

    override val hintsUsed: Int get() = board?.session?.hintsUsed ?: 0

    override val hintEnabled: Boolean
        get() = screen is QueensScreen.Ready && board?.session?.isComplete == false

    /** The §5.5 deduction caption, shown by the shared chrome until the next board action. */
    override val hintCaption: String? get() = board?.hintCaption

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

    override val resultLabel: String?
        get() = (screen as? QueensScreen.Ready)?.ref?.puzzleNumber?.let { "Queens · #$it" } ?: "Queens"

    //endregion

    companion object {
        /** Queens rolls at midnight PT (QueensDefinition pins the zone). */
        val queensCadenceZone: ZoneId = (QueensDefinition.cadence as Cadence.Daily).zone

        private fun unavailableMessage(error: FetchError): String = when (error) {
            is FetchError.Unavailable -> "Couldn't reach LinkedIn, and no copy of today's puzzle is saved yet. Check your connection and try again."
            is FetchError.Throttled -> "LinkedIn is rate-limiting requests right now. Give it a minute and try again."
            is FetchError.Untrusted -> "Today's puzzle failed verification, so it can't be played. Try again later."
        }

        private fun untrustedMessage(error: DecodeError): String =
            "The saved puzzle couldn't be read (${error::class.simpleName}). Try refreshing."
    }
}
