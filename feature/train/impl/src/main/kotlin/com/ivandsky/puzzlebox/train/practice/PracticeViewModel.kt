package com.ivandsky.puzzlebox.train.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One technique's practice (spec §2.2): a [PracticeBoard] cut from a puzzle the device has, newest first (the [source]'s
 * puzzles, then the technique's bundled ones), played to the end by the game's own gestures. The board lives in memory
 * only: turning the device keeps this view model and so the board, while leaving and coming back (a new attempt) or a
 * restore after the system closed the app starts a new board from its cut point (A6, A15). What the board judges is
 * recorded through [recordAnswer] as it happens, one verdict per trained turn, so the in-a-row count outlives the board
 * ([progress], A6); no puzzle is completed, saved or replayed, and nothing but the trainer's own progress is written.
 *
 * With nothing on the device the state is [PracticeState.Empty] when the technique's drill sources name no archive
 * puzzle, else [PracticeState.NeedsConnection]. [tryAgain] is one tap: it asks [archive] to load the first named puzzle
 * that is not on the device, one fetch at most, and opens a board on it. A load that gives no board leaves
 * [PracticeState.CantLoad] naming a puzzle already on the device, which [tryAnother] opens, or
 * [PracticeState.NeedsConnection] again when there is none. A puzzle that is gone, untrusted or never has the technique
 * as its easiest move is not asked for again until every named puzzle has been tried; a busy or offline load asks for
 * the same puzzle on the next tap. No load, failed or not, is ever an answer.
 *
 * [nextBoard] goes on from a solved board: the puzzles on the device not played in this attempt first, then one fetch,
 * then the played ones again, so the player is never stranded.
 */
internal class PracticeViewModel(
    private val game: TrainerGame,
    private val technique: TechniqueId,
    private val source: TrainerPuzzleSource,
    private val archive: TrainerArchive,
    store: TrainerProgressRepository,
    private val recordAnswer: RecordAnswer,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow<PracticeState>(PracticeState.Loading)
    private var attempt: Int? = null
    private var load: Job? = null

    /** The scope the live board runs in; cancelled when the board is replaced. Only touched on the main thread. */
    private var boardLife: CoroutineScope? = null

    /** The archive numbers a load found no use in; only touched on the main thread. */
    private val skipped = mutableSetOf<Int>()

    /** The puzzles a board was opened on in this attempt, so the next board is another one; only touched on the main thread. */
    private val played = mutableSetOf<Puzzle>()

    val state: StateFlow<PracticeState> = mutableState.asStateFlow()

    /** Trained turns in the order the board reported them, so a Wrong never lands before the Right it followed. */
    private val verdicts = Channel<TrainerVerdict>(Channel.UNLIMITED)

    init {
        viewModelScope.launch { for (verdict in verdicts) recordAnswer(game.gameId, technique, verdict) }
    }

    /** The technique's stored progress, which every trained turn changes and which outlives the board. */
    val progress: StateFlow<TechniqueProgress> = store.observe(game.gameId)
        .map { it[technique] ?: TechniqueProgress.NEW }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TechniqueProgress.NEW)

    /**
     * Opens a board for [attempt], the number of the time the technique was opened. The same number again (the screen
     * turned, the host composed anew) leaves the board as it is; a new one drops it and starts another.
     */
    fun start(attempt: Int) {
        if (attempt == this.attempt) return
        this.attempt = attempt
        played.clear()
        replaceBoard {
            if (!openFromDevice()) mutableState.value = withContext(dispatchers.default) { nothingOnDevice() }
        }
    }

    /**
     * The needs-a-connection tap: loads the first named puzzle that is not on the device. Ignored in any other state, so
     * a second tap while the first is loading changes nothing.
     */
    fun tryAgain() {
        if (mutableState.value != PracticeState.NeedsConnection) return
        replaceBoard {
            val numbers = withContext(dispatchers.default) { candidates() }
            var target = nextToFetch(numbers)
            if (target == null && skipped.isNotEmpty()) {
                // Every named puzzle has failed once: begin again from the top rather than strand the player.
                skipped.clear()
                target = nextToFetch(numbers)
            }
            if (target == null) mutableState.value = PracticeState.Empty else resolve(archive.load(game.gameId, technique, target))
        }
    }

    /** The can't-load tap: opens the puzzle already on the device that the state names. Ignored in any other state. */
    fun tryAnother() {
        val current = mutableState.value as? PracticeState.CantLoad ?: return
        replaceBoard { resolve(archive.load(game.gameId, technique, current.alternative)) }
    }

    /** The solved page's Next board: another puzzle for the same technique. Ignored unless a board is up. */
    fun nextBoard() {
        if (mutableState.value !is PracticeState.Practicing) return
        replaceBoard {
            if (openFromDevice()) return@replaceBoard
            val target = nextToFetch(withContext(dispatchers.default) { candidates() })
            if (target != null) {
                resolve(archive.load(game.gameId, technique, target))
                return@replaceBoard
            }
            // Nothing new anywhere: the puzzles played so far come round again.
            played.clear()
            if (!openFromDevice()) mutableState.value = withContext(dispatchers.default) { nothingOnDevice() }
        }
    }

    /** Drops the live board and its scope, shows [PracticeState.Loading], and runs [work], which ends in a new state. Any earlier work is cancelled. */
    private fun replaceBoard(work: suspend () -> Unit) {
        load?.cancel()
        boardLife?.cancel()
        boardLife = null
        mutableState.value = PracticeState.Loading
        load = viewModelScope.launch { work() }
    }

    /** One trained turn's verdict, queued to be recorded in the order it came, for as long as the view model lives, whatever becomes of the board. */
    private fun record(verdict: TrainerVerdict) {
        verdicts.trySend(verdict)
    }

    /** A loaded puzzle opens a board; any other outcome, or a puzzle that never has the technique first, is a failed load. */
    private suspend fun resolve(load: ArchiveLoad) {
        if (load is ArchiveLoad.Loaded && open(load.puzzle)) return
        // Busy and offline are worth the same puzzle again; every other outcome is not.
        val retryable = load is ArchiveLoad.CantLoad &&
            (load.reason == ArchiveLoad.CantLoad.Reason.Busy || load.reason == ArchiveLoad.CantLoad.Reason.Offline)
        if (!retryable) skipped += load.number
        val numbers = withContext(dispatchers.default) { candidates() }
        val alternative = archive.onDevice(game.gameId, technique, numbers).firstOrNull { it !in skipped }
        mutableState.value = if (alternative == null) PracticeState.NeedsConnection else PracticeState.CantLoad(alternative)
    }

    /**
     * Opens a board on the first puzzle on the device that was not played yet and has one: the puzzles the source holds,
     * newest first, then the technique's bundled ones. False when there is none.
     */
    private suspend fun openFromDevice(): Boolean =
        source.puzzles(game.gameId).firstOrNull { openUnplayed(it) } != null ||
            withContext(dispatchers.default) { bundled() }.any { openUnplayed(it) }

    private suspend fun openUnplayed(puzzle: Puzzle): Boolean = puzzle !in played && open(puzzle)

    /** Cuts a board from [puzzle], off the main thread as the walk is CPU work, and shows it; false when [puzzle] never has the technique as its easiest move. */
    private suspend fun open(puzzle: Puzzle): Boolean {
        val parent = viewModelScope.coroutineContext
        val life = CoroutineScope(parent + SupervisorJob(parent[Job]))
        var board: PracticeBoard? = null
        try {
            board = withContext(dispatchers.default) { game.practice(puzzle, technique, life, ::record) }
        } finally {
            if (board == null) life.cancel()
        }
        if (board == null) return false
        played += puzzle
        boardLife = life
        mutableState.value = PracticeState.Practicing(board)
        return true
    }

    /** What the player sees with no board to play: nothing to fetch at all, or a fetch to ask for. */
    private fun nothingOnDevice(): PracticeState =
        if (candidates().isEmpty()) PracticeState.Empty else PracticeState.NeedsConnection

    /** The first of [numbers] that is neither on the device nor already found useless. */
    private suspend fun nextToFetch(numbers: List<Int>): Int? {
        val onDevice = archive.onDevice(game.gameId, technique, numbers).toSet()
        return numbers.firstOrNull { it !in onDevice && it !in skipped }
    }

    /** The archive puzzle numbers the technique's drill sources name, the most target steps first. */
    private fun candidates(): List<Int> = game.drillSources
        .flatMap { it.candidates(technique) }
        .filterIsInstance<DrillCandidate.Archive>()
        .sortedByDescending { it.targetSteps }
        .map { it.number }
        .distinct()

    /** The puzzles the technique's drill sources bundle, the most target steps first: always on the device, so they never need a fetch. */
    private fun bundled(): List<Puzzle> = game.drillSources
        .flatMap { it.candidates(technique) }
        .filterIsInstance<DrillCandidate.Bundled>()
        .sortedByDescending { it.targetSteps }
        .map { it.puzzle }
}
