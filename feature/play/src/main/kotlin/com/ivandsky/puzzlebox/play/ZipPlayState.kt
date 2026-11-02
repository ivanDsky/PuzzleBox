package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.ZipToday
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
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
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The play screen's async board states (A7/A8) — degrade states are first-class. */
sealed interface ZipScreen {
    data object Loading : ZipScreen

    /** Nothing playable today; [message] drives the error copy. Retry re-runs the fetch. */
    data class Unavailable(val message: String) : ZipScreen

    /** Board in hand. [degraded] non-null = playing the SAVED puzzle because the fetch
     *  failed (capture-probe live path) — surfaced as a banner, the board stays playable. */
    data class Ready(val puzzle: ZipPuzzle, val ref: ZipPuzzleRef?, val degraded: FetchError?) : ZipScreen
}

/**
 * The Zip play state holder (ticket 13): today's board → engine → win flow. Owns the
 * [ZipSession] as the single source of truth for path/hints/timer; every committed change
 * round-trips through [onSessionChanged] so the route can persist bytes across
 * process death and rotation (A19 session codec, F2 §2 timer semantics).
 *
 * Win flow (F2 §7): the [ZipSession.completedAt] transition emits ONE
 * [CompletionRecord] via [CompletionRecordEmitter] — fire-and-observe, never blocking
 * the win overlay. [recordBadge] is null while the write is in flight, then the A4
 * verdict for THIS play (true = first in-app completion, false = replay).
 */
class ZipPlayState(
    private val daily: ZipDailyRepository,
    private val completions: CompletionRepository,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
    private val today: () -> LocalDate = { LocalDate.now(zipCadenceZone()) },
    private val onSessionChanged: (ZipSession?) -> Unit = {},
) {

    var screen by mutableStateOf<ZipScreen>(ZipScreen.Loading)
        private set
    var session by mutableStateOf<ZipSession?>(null)
        private set
    var hintCell by mutableStateOf<Int?>(null)
        private set

    /** Ticket 28: the hint arrow's placed segment (tail → new head), transient — gone on
     *  the next input or after ~2s (the screen fades it via [clearHintArrow]). */
    var hintArrow by mutableStateOf<HintArrow?>(null)
        private set
    var rejection by mutableStateOf<ZipRendererModel.RejectedMove?>(null)
        private set
    var recordBadge by mutableStateOf<Boolean?>(null)
        private set
    var recordWriteFailed by mutableStateOf(false)
        private set
    var nowMs by mutableStateOf(0L)
        private set

    private val rules = zipPlugin.rules as ZipRules // the plugin's rules ARE ZipRules
    private var emitter: CompletionRecordEmitter? = null

    /** Whether a record already existed for the current ref BEFORE this session's win —
     *  the A4 badge is derived from this baseline, never from the stored rows. */
    private var baselineHadRecord: Boolean? = null

    //region input (F2 §7: candidate moves in, authoritative validation here)

    fun onDragEvent(event: DragEvent) {
        hintArrow = null // ticket 28: the arrow dies on the next input, always
        val current = session ?: return
        val ready = screen as? ZipScreen.Ready ?: return
        if (current.isComplete) return
        val board = ready.puzzle.board
        when (event) {
            is DragEvent.Extend -> {
                val move = ZipMove.Extend(cellOf(event.cell, board.n))
                when (val verdict = rules.validateMove(move, ZipState(ready.puzzle, current.path))) {
                    is ZipValidation.Valid -> commit(current.withMove(move, now()))
                    is ZipValidation.Invalid -> reject(current, cellOf(event.cell, board.n), verdict.reason)
                }
            }

            is DragEvent.Retract -> {
                val target = current.path.getOrNull(event.index) ?: return
                commit(current.withMove(ZipMove.RetractTo(target), now()))
            }

            // R1 tap on the first traced cell: the trace empties to the start state.
            DragEvent.Clear -> commit(current.withMove(ZipMove.Reset, now()))
        }
    }

    //region load / restore

    /** Fetch-today → board (A7/A8): Fresh plays; Cached plays with a degrade banner;
     *  Unavailable is the error state; an untrustable payload is never playable (A16). */
    fun load() {
        screen = ZipScreen.Loading
        session = null
        hintCell = null
        hintArrow = null
        rejection = null
        recordBadge = null
        recordWriteFailed = false
        baselineHadRecord = null
        emitter = null
        scope.launch {
            when (val today = daily.today()) {
                is ZipToday.Fresh -> adopt(today.puzzle, degraded = null)
                is ZipToday.Cached -> adopt(today.puzzle, degraded = today.error)
                is ZipToday.Unavailable -> screen = ZipScreen.Unavailable(unavailableMessage(today.error))
            }
        }
    }

    /** Rotation/process-death restore: the session codec carries the puzzle BY VALUE, so
     *  a corrupt save falls back to a normal load; a restored COMPLETED session shows the
     *  win without re-emitting (emission happens on the transition only). */
    fun adoptRestored(bytes: ByteArray) {
        when (val restored = ZipSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value
                emitter = null
                session = s
                screen = ZipScreen.Ready(s.puzzle, s.ref, degraded = null)
                if (!s.isComplete) refreshBaseline(s.ref)
            }

            is DecodeResult.Err -> load()
        }
    }

    private suspend fun adopt(puzzle: Puzzle, degraded: FetchError?) {
        val zip = when (val decoded = ZipCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> {
                screen = ZipScreen.Unavailable(untrustedMessage(decoded.error))
                return
            }
        }
        val ref = resolveRef()
        screen = ZipScreen.Ready(zip, ref, degraded)
        session = ZipSession(ref = ref, puzzle = zip)
        refreshBaseline(ref)
    }

    /** Record identity = gameId zip + day/puzzleNumber (ADR-0004): the repository persists
     *  both at fetch time and history rows carry them; today's row (in Zip's cadence
     *  zone) is the identity of the board we just loaded. */
    private suspend fun resolveRef(): ZipPuzzleRef? {
        val day = today()
        val entry = daily.observeHistory().first().firstOrNull { it.day == day }
        return ZipPuzzleRef(entry?.day ?: day, entry?.puzzleNumber)
    }

    private fun refreshBaseline(ref: ZipPuzzleRef?) {
        if (ref == null) return
        scope.launch {
            baselineHadRecord = completions.observeRecords().first().any { it.puzzleRef == ref }
        }
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
        val ready = screen as? ZipScreen.Ready ?: return
        if (current.isComplete) return
        var next = current
        var resolved = rules.hint(ZipState(ready.puzzle, next.path))
        if (resolved is ZipHint.Backtrack) {
            next = next.withMove(
                if (resolved.trimTo < 0) ZipMove.Reset else ZipMove.RetractTo(next.path[resolved.trimTo]),
                now(),
            )
            resolved = rules.hint(ZipState(ready.puzzle, next.path))
        }
        val step = resolved as? ZipHint.Step
        if (step != null) {
            val move = ZipMove.Extend(step.cell)
            if (rules.validateMove(move, ZipState(ready.puzzle, next.path)) is ZipValidation.Valid) {
                next = next.withMove(move, now())
                next.path.getOrNull(next.path.size - 2)?.let { tail ->
                    hintArrow = HintArrow(headIdx = step.cell.idx, tailIdx = tail.idx)
                }
            }
        }
        next = next.withHintApplied()
        commit(next)
        hintCell = (resolved as? ZipHint.Step)?.cell?.idx
    }

    /** The screen calls this after the hold/fade window (reduced motion: immediately). */
    fun clearHintArrow() {
        hintArrow = null
    }

    //endregion

    //region timer (F2 §2: active foreground play only)

    fun tick() {
        nowMs = now().toEpochMilli()
    }

    fun onBackground() {
        val current = session ?: return
        updateSession(current.onBackground(now()))
    }

    fun onForeground() {
        val current = session ?: return
        updateSession(current.onForeground(now()))
    }

    /** What TimerBar draws right now (banked + running window). Never negative:
     *  the first frame after the initial Extend observes runningSince before the
     *  tick loop's first tick() advances nowMs. */
    fun displayedElapsedMs(): Long =
        session?.displayedElapsedMs(Instant.ofEpochMilli(nowMs))?.coerceAtLeast(0L) ?: 0L

    //endregion

    //region win flow

    /** Win overlay's "Play again": a fresh session, a new emitter lifetime — replays
     *  emit again (A4), and the badge resets until the new write lands. The A4 baseline
     *  is re-read: whether a record existed BEFORE this new play. */
    fun replay() {
        val ready = screen as? ZipScreen.Ready ?: return
        emitter = null
        recordBadge = null
        recordWriteFailed = false
        hintCell = null
        hintArrow = null
        rejection = null
        baselineHadRecord = null
        refreshBaseline(ready.ref)
        commit(ZipSession(ref = ready.ref, puzzle = ready.puzzle))
    }

    /** Retry after a failed record write: a new emitter (the old one deduped this ref). */
    fun retryRecordWrite() {
        val completed = session?.takeIf { it.isComplete } ?: return
        emitRecord(record = zipPlugin.score.completion(completed) ?: return)
    }

    private fun onWin(completed: ZipSession) {
        emitRecord(record = zipPlugin.score.completion(completed) ?: return)
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
    fun rendererModel(): ZipRendererModel? {
        val current = session ?: return null
        val ready = screen as? ZipScreen.Ready ?: return null
        val hint = hintCell?.let { ZipHint.Step(Cell(it)) }
        return ZipRendererModel.of(ZipState(ready.puzzle, current.path), hint, rejection)
    }

    /** Versioned session bytes for the route's rememberSaveable (A19/F2 §2). */
    fun persist(): ByteArray? = session?.let(ZipSessionCodec::serialize)

    //endregion

    //region internals

    private fun commit(next: ZipSession) {
        hintCell = null
        updateSession(next)
    }

    private fun updateSession(next: ZipSession) {
        val won = next.isComplete && session?.isComplete != true
        session = next
        onSessionChanged(next)
        if (won) onWin(next)
    }

    private fun reject(current: ZipSession, cell: Cell, reason: ZipValidation.Reason) {
        updateSession(current.withRejection()) // mistake analytics only, never scored (A3)
        rejection = ZipRendererModel.RejectedMove(cell.idx, reason)
        scope.launch {
            delay(REJECTION_CLEAR_MS)
            if (rejection?.cellIdx == cell.idx) rejection = null
        }
    }

    private fun cellOf(cell: GridCell, n: Int) = Cell(cell.row * n + cell.col)

    //endregion

    private companion object {
        const val REJECTION_CLEAR_MS = 600L

        /** Zip rolls at midnight PT (research 01; ZipDefinition pins the zone). */
        fun zipCadenceZone() = (ZipDefinition.cadence as Cadence.Daily).zone
    }
}

/** The hint arrow's geometry (ticket 28): the placed segment tail → new head, as row-major indices. */
data class HintArrow(val headIdx: Int, val tailIdx: Int)

/** Copy for the first-class degrade states. */
fun unavailableMessage(error: FetchError): String = when (error) {
    is FetchError.Unavailable -> "Couldn't reach LinkedIn, and no copy of today's puzzle is saved yet. Check your connection and try again."
    is FetchError.Throttled -> "LinkedIn is rate-limiting requests right now. Give it a minute and try again."
    is FetchError.Untrusted -> "Today's puzzle failed verification, so it can't be played. Try again later."
}

private fun untrustedMessage(error: DecodeError): String =
    "The saved puzzle couldn't be read (${error::class.simpleName}). Try refreshing."

/** Banner copy when a captured day is played because the fetch failed (A7/A8). */
fun degradeBannerMessage(error: FetchError): String = when (error) {
    is FetchError.Unavailable -> "Offline — playing today's saved puzzle."
    is FetchError.Throttled -> "LinkedIn is rate-limiting — playing today's saved puzzle."
    is FetchError.Untrusted -> "Fresh puzzle was rejected — playing the saved copy."
}
