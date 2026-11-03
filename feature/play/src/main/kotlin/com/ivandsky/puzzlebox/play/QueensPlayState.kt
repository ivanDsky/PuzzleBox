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
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensBoardState
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * Owner of the Queens play flow's [QueensPlayState] (zip [PlayFlow] precedent, ticket 23):
 * the state must outlive the play entry's composition so the record write is
 * fire-and-observe, never cancelled by navigation. A Koin single is the process
 * lifetime; [end] releases the scope on a mid-game exit.
 */
class QueensPlayFlow(
    private val daily: QueensDailyRepository,
    private val completions: CompletionRepository,
) {
    private var flowScope: CoroutineScope? = null

    var state: QueensPlayState? by mutableStateOf(null)
        private set

    /** The flow's state, created on first entry and reused until [end]. A re-entered
     *  entry (rotation) rebinds the session saver to ITS rememberSaveable and backfills
     *  it with the live session (R-4), so a process death mid-play still restores. */
    fun start(onSessionChanged: (QueensSession?) -> Unit): QueensPlayState {
        state?.let { existing ->
            existing.onSessionChanged = onSessionChanged
            onSessionChanged(existing.board?.session)
            return existing
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        flowScope = scope
        return QueensPlayState(
            daily = daily,
            completions = completions,
            scope = scope,
        ).also {
            it.onSessionChanged = onSessionChanged
            state = it
        }
    }

    /** Mid-game exit: the next play entry must fetch fresh, never resume. */
    fun end() {
        flowScope?.cancel()
        flowScope = null
        state = null
    }
}

/**
 * The Queens play state holder (queens-10 wiring; zip [ZipPlayState] shape): today's
 * board (queens-06 offline-first repository) → a real [QueensSession] (queens-04) →
 * the queens-08 [QueensBoardState] interaction holder. The holder owns only the fetch /
 * restore / record wiring — every game decision stays in the session and the board
 * state (A10–A12 provenance, A14 Auto-cross, §5 interaction set).
 *
 * Win flow (fix round 6 — zip's win→result-route pattern): the board state hands ONE
 * [CompletionRecord] (built by the plugin's score model on the completedAt transition)
 * to [onWinRecord]; the write stays fire-and-observe in the
 * flow scope — navigation (to the result route) can never cancel it. [recordBadge] is
 * null while the write is in flight, then the A4 verdict against the session-start
 * baseline; [recordWriteFailed] surfaces the retry affordance on the result route.
 * A restored completed session never re-emits (transition-only) and refreshes no
 * baseline.
 */
class QueensPlayState(
    private val daily: QueensDailyRepository,
    private val completions: CompletionRepository,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
) {

    var screen by mutableStateOf<QueensScreen>(QueensScreen.Loading)
        private set

    /**
     * The entry's session-persistence callback (R-4, fix round 4): the play entry
     * serializes every session commit into its `rememberSaveable`. A re-entered entry
     * (rotation) brings a NEW saveable state, so [QueensPlayFlow.start] REBINDS this
     * callback to the new entry and backfills it with the live session — otherwise the
     * board keeps serializing into the dead first composition and a process death
     * during play loses the session (the "rotation restarts the game" defect).
     */
    var onSessionChanged: (QueensSession?) -> Unit = {}

    /** The live interaction holder, null until a session is adopted. */
    var board by mutableStateOf<QueensBoardState?>(null)
        private set

    /** A4 record badge: null = write in flight (or baseline unknown); true/false = the
     *  verdict against the session-start baseline (new record vs beat replay). */
    var recordBadge by mutableStateOf<Boolean?>(null)
        private set

    /** The record write failed — the result route offers a retry. */
    var recordWriteFailed by mutableStateOf(false)
        private set

    private var emitter: CompletionRecordEmitter? = null

    /** Whether a record already existed for the current ref BEFORE this session's win —
     *  the A4 badge is derived from this baseline, never from the stored rows. */
    private var baselineHadRecord: Boolean? = null

    //region load / restore

    /** Fetch-today → board (A7/A8): Fresh plays; Cached plays with a degrade banner;
     *  Unavailable is the error state; an untrustable payload is never playable (A16). */
    fun load() {
        screen = QueensScreen.Loading
        board = null
        emitter = null
        recordBadge = null
        recordWriteFailed = false
        baselineHadRecord = null
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
    fun adoptRestored(bytes: ByteArray) {
        when (val restored = QueensSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value
                emitter = null
                recordBadge = null
                recordWriteFailed = false
                baselineHadRecord = null
                board = boardFor(s)
                screen = QueensScreen.Ready(s.puzzle, s.ref, degraded = null)
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
        board = boardFor(QueensSession(ref = ref, puzzle = queens))
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
    )

    /** Result route's "Play again" parity (zip replay): a fresh session, a new emitter
     *  lifetime — replays emit again (A4), and the badge resets until the new write
     *  lands. The A4 baseline is re-read: whether a record existed BEFORE this new play. */
    fun replay() {
        val ready = screen as? QueensScreen.Ready ?: return
        emitter = null
        recordBadge = null
        recordWriteFailed = false
        baselineHadRecord = null
        refreshBaseline(ready.ref)
        board = boardFor(QueensSession(ref = ready.ref, puzzle = ready.puzzle))
    }

    /** Retry after a failed record write: a new emitter (the old one deduped this ref). */
    fun retryRecordWrite() {
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

    private companion object {
        fun unavailableMessage(error: FetchError): String = when (error) {
            is FetchError.Unavailable -> "Couldn't reach LinkedIn, and no copy of today's puzzle is saved yet. Check your connection and try again."
            is FetchError.Throttled -> "LinkedIn is rate-limiting requests right now. Give it a minute and try again."
            is FetchError.Untrusted -> "Today's puzzle failed verification, so it can't be played. Try again later."
        }

        private fun untrustedMessage(error: DecodeError): String =
            "The saved puzzle couldn't be read (${error::class.simpleName}). Try refreshing."

        /** Queens rolls at midnight PT (QueensDefinition pins the zone). */
        fun today(): LocalDate = LocalDate.now((QueensDefinition.cadence as Cadence.Daily).zone)
    }
}
