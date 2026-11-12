package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.playTimeMs
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.BROKEN_PUZZLE_MESSAGE
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec
import com.ivandsky.puzzlebox.games.strands.engine.StrandsHint
import com.ivandsky.puzzlebox.games.strands.engine.StrandsHints
import com.ivandsky.puzzlebox.games.strands.engine.StrandsMove
import com.ivandsky.puzzlebox.games.strands.engine.StrandsRules
import com.ivandsky.puzzlebox.games.strands.engine.StrandsVerdict
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant

/**
 * The Strands session holder (Tango/Queens controller shape). The [StrandsSession] is the
 * single source of truth for found words, non-theme words, hints and banked time; every
 * committed change round-trips through [PlayEnvironment.onSessionChanged] so the play
 * feature can restore it after process death. The live [trace] is process-local on
 * purpose: an uncommitted trace is never persisted (A30).
 *
 * No undo (A15): found words are permanent. The hint economy lives in the engine; this
 * class only spends a [StrandsHint.Reveal] the engine hands it.
 *
 * Win: the incomplete → complete transition hands one completion record to
 * [PlayEnvironment.onWon].
 */
class StrandsPlayController(
    private val env: PlayEnvironment,
    private val now: () -> Instant = Instant::now,
) : GamePlayController, PlayClock {

    var session by mutableStateOf<StrandsSession?>(null)
        private set
    var trace by mutableStateOf(Trace())
        private set
    var slot: WordSlot by mutableStateOf(WordSlot.Empty)
        private set
    /** The word just found, until the board takes it with [takeFound]; then null, so a re-entered board rests. */
    var lastFound by mutableStateOf<FoundEvent?>(null)
        private set

    /** The word just submitted, still on the board while it plays out; null once a new trace starts or the board resets. */
    internal var traceEnd by mutableStateOf<TraceEnd?>(null)
        private set
    var hintDialog by mutableStateOf<HintDialog?>(null)
        private set
    var nowMs by mutableStateOf(0L)
        private set

    /** Compose state on purpose: the clock loop keys off [clockWindow], so it must be observable. */
    private var runningSince: Instant? by mutableStateOf(null)
    private val moveLog = StrandsMoveLog(env.moveLog, now) { runningSince }
    private var verdictSeq = 0
    private var foundSeq = 0

    /** The board at rest, without the live trace: it changes only when the game state does, never on a trace step. */
    internal val baseModel: StrandsRendererModel by derivedStateOf {
        session?.let { StrandsRendererModel.of(it.state) } ?: EMPTY_MODEL
    }

    /** The drawable board, live trace included; an empty 0×0 board before a puzzle is adopted. The board draws [baseModel]. */
    val model: StrandsRendererModel by derivedStateOf {
        session?.let { StrandsRendererModel.of(it.state, trace.path) } ?: EMPTY_MODEL
    }

    val header: StrandsHeader?
        get() = session?.let {
            val number = (it.ref?.key as? PuzzleKey.Daily)?.puzzleNumber // a custom puzzle has none
            StrandsHeader(it.state.puzzle.clue, number, StrandsCopy.credit(it.state.puzzle.constructors))
        }

    val hint: HintUi
        get() = session?.state?.let { HintUi(StrandsHints.available(it), StrandsHints.meterThirds(it)) }
            ?: HintUi(count = 0, meterThirds = 0)

    /** The words found so far, in the order found, for the tablet side panel's list. */
    val foundWords: List<FoundWord>
        get() = session?.state?.let { s -> s.found.map { FoundWord(it, spangram = it == s.puzzle.spangram.text) } }.orEmpty()

    /** The hints the finished board took; null while it is unsolved (A36 rating word). */
    val solvedHints: Int?
        get() = session?.state?.takeIf { it.isComplete }?.hintsUsed

    override val isComplete: Boolean get() = session?.state?.isComplete == true

    /** Hands the found word to the board once: rotation, re-entry or relayout never replays it. */
    fun takeFound(): FoundEvent? = lastFound.also { lastFound = null }

    //region input

    fun tap(at: Coord) = onTrace(TraceInput.tap(trace, at))

    fun onTrace(step: TraceStep) {
        val current = session ?: return
        if (current.state.isComplete) return
        when (step) {
            is TraceStep.Updated -> {
                trace = step.trace
                if (step.trace.path.isNotEmpty()) traceEnd = null
                slot = if (step.trace.path.isEmpty()) {
                    WordSlot.Empty
                } else {
                    WordSlot.Tracing(step.trace.path.map(current.state.puzzle::letterAt).joinToString(""))
                }
            }

            TraceStep.Cleared -> {
                trace = Trace()
                traceEnd = null
                slot = WordSlot.Empty
            }

            is TraceStep.Submit -> submit(current, step.path)
        }
    }

    private fun submit(current: StrandsSession, path: List<Coord>) {
        val verdict = StrandsRules.validate(StrandsMove(path), current.state) as StrandsVerdict
        trace = Trace()
        slot = StrandsVerdictSlot.of(verdict, ++verdictSeq)
        val repeatsFound = verdict == StrandsVerdict.AlreadyFound &&
            path.map(current.state.puzzle::letterAt).joinToString("") in current.state.found
        traceEnd = TraceEndKind.of(verdict)?.let { TraceEnd(path, it, verdictSeq, pulse = repeatsFound) }
        when (verdict) {
            is StrandsVerdict.ThemeWord -> lastFound = FoundEvent(verdict.word.cells, spangram = false, seq = ++foundSeq)
            is StrandsVerdict.Spangram -> lastFound = FoundEvent(verdict.word.cells, spangram = true, seq = ++foundSeq)
            else -> Unit
        }
        val next = moveLog.submit(current, path)
        if (next !== current) commit(next)
    }

    fun pressHint() {
        val current = session ?: return
        if (current.state.isComplete) return
        when (val hint = StrandsRules.hint(current.state) as StrandsHint) {
            is StrandsHint.Reveal -> {
                val next = moveLog.hint(current, hint.hint)
                if (next !== current) commit(next)
            }

            StrandsHint.OnBoard -> hintDialog = HintDialog.OnBoard
            StrandsHint.NoneAvailable -> hintDialog = HintDialog.NoneAvailable
        }
    }

    fun dismissHintDialog() {
        hintDialog = null
    }

    //endregion

    //region adopt / restore / replay

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        if (puzzle.gameId != StrandsDefinition.id) return Adopt.Rejected(BROKEN_PUZZLE_MESSAGE)
        val strands = when (val decoded = StrandsCodec.decode(puzzle.payload)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return Adopt.Rejected(BROKEN_PUZZLE_MESSAGE)
        }
        resetTransient()
        runningSince = null
        // Persist from load, not from the first word: the saved play must exist at once.
        commit(StrandsSession(ref = ref as StrandsPuzzleRef, state = StrandsState(strands)))
        openClock() // the clock runs from the moment the puzzle opens
        return Adopt.Adopted
    }

    /** A restored finished session shows the win without emitting again (emission is on the transition only). */
    override fun adoptRestored(bytes: ByteArray): Restored? =
        when (val restored = StrandsSessionCodec.deserialize(bytes)) {
            is DecodeResult.Ok -> {
                val s = restored.value
                resetTransient()
                session = s
                // The running window is process-local: a restored game in progress reopens it now.
                runningSince = null
                if (!s.state.isComplete) openClock()
                Restored(s.ref)
            }

            is DecodeResult.Err -> null
        }

    /** Result's "Play again": the same puzzle from scratch with a fresh clock window; its next win is a new transition. */
    override fun playAgain() {
        val current = session ?: return
        resetTransient()
        runningSince = null
        commit(StrandsSession(ref = current.ref, state = StrandsState(current.state.puzzle)))
        openClock()
    }

    private fun resetTransient() {
        trace = Trace()
        traceEnd = null
        slot = WordSlot.Empty
        lastFound = null
        hintDialog = null
    }

    //endregion

    //region clock (active foreground play only)

    override val clockWindow: Any? get() = runningSince

    override fun tick() {
        nowMs = now().toEpochMilli()
    }

    override fun onBackground() {
        val current = session ?: return
        if (runningSince != null) commit(foldRunning(current))
    }

    override fun onForeground() {
        val current = session ?: return
        if (runningSince == null && !current.state.isComplete) openClock()
    }

    /** Banked time plus the running window. Never negative. */
    override fun displayedElapsedMs(): Long {
        val current = session ?: return 0L
        return playTimeMs(current.elapsedMs, runningSince, Instant.ofEpochMilli(nowMs))
    }

    /** Opens the running window now, unless one is already open. */
    private fun openClock() {
        if (runningSince != null) return
        val start = now()
        runningSince = start
        nowMs = start.toEpochMilli()
    }

    private fun foldRunning(target: StrandsSession): StrandsSession {
        val since = runningSince ?: return target
        runningSince = null
        return target.copy(elapsedMs = playTimeMs(target.elapsedMs, since, now()))
    }

    //endregion

    private fun commit(next: StrandsSession) {
        val won = next.state.isComplete && session?.state?.isComplete != true
        val settled = if (won) foldRunning(next) else next
        session = settled
        env.onSessionChanged(StrandsSessionCodec.serialize(settled))
        if (won) env.onWon(strandsPlugin.score.completion(settled) ?: return)
    }

    private companion object {
        val EMPTY_MODEL = StrandsRendererModel(
            rows = 0,
            cols = 0,
            cells = emptyList(),
            links = emptyList(),
            foundCount = 0,
            totalCount = 0,
            complete = false,
        )
    }
}
