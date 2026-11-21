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
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long a finished board stays on screen, so its last step and the Done band can be seen, before the next board opens on its own. */
internal const val BOARD_DONE_PAUSE_MS = 1_200L

/** The focus boards every chapter starts with. */
private const val FOCUS_BOARDS = 4

/** One trained turn's verdict with the technique it trains: the technique moves on while a chapter goes, so it travels with the verdict. */
private class Turn(val technique: TechniqueId, val verdict: TrainerVerdict)

/**
 * A game's practice, a technique's chapter at a time (spec §2.2): four focus boards then one review board, the first
 * technique of the game's catalog having the four focus boards only. Each board is a [PracticeBoard] cut from a puzzle
 * the device has, newest first (the [source]'s puzzles, then the technique's bundled ones), played with the game's own
 * gestures until it says [PracticeStatus.Done]. After [BOARD_DONE_PAUSE_MS] the next board of the chapter opens on its
 * own; after the chapter's last board the next technique in the catalog opens at its first board, and after the
 * catalog's last technique the state is [PracticeState.Finished]. [chapter] says which technique and board is up.
 * The boards live in memory only: turning the device keeps this view model and so the board, while leaving and coming
 * back (a new attempt) or a restore after the system closed the app starts again at the opened technique's first board
 * (A6, A15). What a board judges is recorded through [recordAnswer] as it happens, one verdict per trained turn, so the
 * in-a-row count outlives the board ([progress], A6); no puzzle is completed, saved or replayed, and nothing but the
 * trainer's own progress is written.
 *
 * With nothing on the device the state is [PracticeState.Empty] when the technique's drill sources name no archive
 * puzzle, else [PracticeState.NeedsConnection]. [tryAgain] is one tap: it asks [archive] to load the first named puzzle
 * that is not on the device, one fetch at most, and opens a board on it. A load that gives no board leaves
 * [PracticeState.CantLoad] naming a puzzle already on the device, which [tryAnother] opens, or
 * [PracticeState.NeedsConnection] again when there is none. A puzzle that is gone, untrusted or never has the technique
 * as its easiest move is not asked for again until every named puzzle has been tried; a busy or offline load asks for
 * the same puzzle on the next tap. No load, failed or not, is ever an answer.
 *
 * Each board of a chapter is cut from the puzzles on the device not played in this chapter first, then one fetch, then
 * the played ones again, so the player is never stranded and a puzzle only comes round twice when nothing else is left.
 */
internal class PracticeViewModel(
    private val game: TrainerGame,
    technique: TechniqueId,
    private val source: TrainerPuzzleSource,
    private val archive: TrainerArchive,
    store: TrainerProgressRepository,
    private val recordAnswer: RecordAnswer,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val catalog = game.catalog()
    private val opened = catalog.indexOfFirst { it.id == technique }.also { check(it >= 0) { "$technique is not in the catalog" } }

    /** The catalog position of the technique being trained and the board of its chapter, counted from 0; only touched on the main thread. */
    private var techniqueIndex = opened
    private var boardIndex = 0

    private val mutableState = MutableStateFlow<PracticeState>(PracticeState.Loading)
    private val mutableChapter = MutableStateFlow(currentChapter())
    private var attempt: Int? = null
    private var load: Job? = null

    /** The scope the live board runs in; cancelled when the board is replaced. Only touched on the main thread. */
    private var boardLife: CoroutineScope? = null

    /** The archive numbers a load found no use in for the technique being trained; only touched on the main thread. */
    private val skipped = mutableSetOf<Int>()

    /** The puzzles a board was opened on in this chapter, so the next board is another one; only touched on the main thread. */
    private val played = mutableSetOf<Puzzle>()

    val state: StateFlow<PracticeState> = mutableState.asStateFlow()

    /** The technique and board practice is on, which the title row shows. */
    val chapter: StateFlow<PracticeChapter> = mutableChapter.asStateFlow()

    /** Trained turns in the order the board reported them, so a Wrong never lands before the Right it followed. */
    private val verdicts = Channel<Turn>(Channel.UNLIMITED)

    init {
        viewModelScope.launch { for (turn in verdicts) recordAnswer(game.gameId, turn.technique, turn.verdict) }
    }

    /** The stored progress of the technique being trained, which every trained turn changes and which outlives the board. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val progress: StateFlow<TechniqueProgress> = mutableChapter.map { it.technique.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> store.observe(game.gameId).map { it[id] ?: TechniqueProgress.NEW } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TechniqueProgress.NEW)

    /**
     * Opens the first board for [attempt], the number of the time the technique was opened. The same number again (the
     * screen turned, the host composed anew) leaves the board as it is; a new one drops it and starts the opened
     * technique's chapter over.
     */
    fun start(attempt: Int) {
        if (attempt == this.attempt) return
        this.attempt = attempt
        moveTo(opened, 0)
        replaceBoard {
            if (!openFromDevice()) mutableState.value = withContext(dispatchers.default) { nothingOnDevice(current) }
        }
    }

    /**
     * The needs-a-connection tap: loads the first named puzzle that is not on the device. Ignored in any other state, so
     * a second tap while the first is loading changes nothing.
     */
    fun tryAgain() {
        if (mutableState.value != PracticeState.NeedsConnection) return
        replaceBoard {
            val technique = current
            val numbers = withContext(dispatchers.default) { candidates(technique) }
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
        val state = mutableState.value as? PracticeState.CantLoad ?: return
        replaceBoard { resolve(archive.load(game.gameId, current, state.alternative)) }
    }

    /** The kinds of board a chapter of the technique at [index] has, in order: the focus boards, then a review unless it is the catalog's first technique. */
    private fun kinds(index: Int): List<BoardKind> = List(FOCUS_BOARDS) { BoardKind.Focus } + if (index == 0) emptyList() else listOf(BoardKind.Review)

    private val current: TechniqueId get() = catalog[techniqueIndex].id

    private fun currentChapter() = PracticeChapter(catalog[techniqueIndex], boardIndex + 1, kinds(techniqueIndex).size)

    /** Puts practice at board [board] (from 0) of the chapter of the technique at [index]; a technique that changes starts over on which puzzles failed and which were played. */
    private fun moveTo(index: Int, board: Int) {
        if (index != techniqueIndex) skipped.clear()
        if (index != techniqueIndex || board == 0) played.clear()
        techniqueIndex = index
        boardIndex = board
        mutableChapter.value = currentChapter()
    }

    /** A finished board goes on: the chapter's next board, the next technique's first, or the finished page after the last. */
    private fun advance() {
        when {
            boardIndex < kinds(techniqueIndex).lastIndex -> moveTo(techniqueIndex, boardIndex + 1)
            techniqueIndex < catalog.lastIndex -> moveTo(techniqueIndex + 1, 0)
            else -> {
                replaceBoard { mutableState.value = PracticeState.Finished }
                return
            }
        }
        replaceBoard {
            if (openFromDevice()) return@replaceBoard
            val target = nextToFetch(withContext(dispatchers.default) { candidates(current) })
            if (target != null) {
                resolve(archive.load(game.gameId, current, target))
                return@replaceBoard
            }
            // Nothing new anywhere: the puzzles played so far come round again.
            played.clear()
            if (!openFromDevice()) mutableState.value = withContext(dispatchers.default) { nothingOnDevice(current) }
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
    private fun record(technique: TechniqueId, verdict: TrainerVerdict) {
        verdicts.trySend(Turn(technique, verdict))
    }

    /** A loaded puzzle opens a board; any other outcome, or a puzzle that never has the technique first, is a failed load. */
    private suspend fun resolve(load: ArchiveLoad) {
        if (load is ArchiveLoad.Loaded && open(load.puzzle)) return
        // Busy and offline are worth the same puzzle again; every other outcome is not.
        val retryable = load is ArchiveLoad.CantLoad &&
            (load.reason == ArchiveLoad.CantLoad.Reason.Busy || load.reason == ArchiveLoad.CantLoad.Reason.Offline)
        if (!retryable) skipped += load.number
        val technique = current
        val numbers = withContext(dispatchers.default) { candidates(technique) }
        val alternative = archive.onDevice(game.gameId, technique, numbers).firstOrNull { it !in skipped }
        mutableState.value = if (alternative == null) PracticeState.NeedsConnection else PracticeState.CantLoad(alternative)
    }

    /**
     * Opens a board on the first puzzle on the device that was not played yet and has one: the puzzles the source holds,
     * newest first, then the technique's bundled ones. False when there is none.
     */
    private suspend fun openFromDevice(): Boolean =
        source.puzzles(game.gameId).firstOrNull { openUnplayed(it) } != null ||
            withContext(dispatchers.default) { bundled(current) }.any { openUnplayed(it) }

    private suspend fun openUnplayed(puzzle: Puzzle): Boolean = puzzle !in played && open(puzzle)

    /**
     * Cuts a board of the current kind from [puzzle], off the main thread as the walk is CPU work, and shows it; false
     * when [puzzle] never has the technique as its easiest move. The board's finish is watched for as long as it lives.
     */
    private suspend fun open(puzzle: Puzzle): Boolean {
        val parent = viewModelScope.coroutineContext
        val life = CoroutineScope(parent + SupervisorJob(parent[Job]))
        val technique = current
        val kind = kinds(techniqueIndex)[boardIndex]
        var board: PracticeBoard? = null
        try {
            board = withContext(dispatchers.default) { game.practice(puzzle, technique, kind, life) { record(technique, it) } }
        } finally {
            if (board == null) life.cancel()
        }
        val live = board ?: return false
        played += puzzle
        boardLife = life
        mutableState.value = PracticeState.Practicing(live)
        life.launch {
            live.status.first { it is PracticeStatus.Done }
            delay(BOARD_DONE_PAUSE_MS)
            if (boardLife === life) advance()
        }
        return true
    }

    /** What the player sees with no board to play for [technique]: nothing to fetch at all, or a fetch to ask for. */
    private fun nothingOnDevice(technique: TechniqueId): PracticeState =
        if (candidates(technique).isEmpty()) PracticeState.Empty else PracticeState.NeedsConnection

    /** The first of [numbers] that is neither on the device nor already found useless. */
    private suspend fun nextToFetch(numbers: List<Int>): Int? {
        val onDevice = archive.onDevice(game.gameId, current, numbers).toSet()
        return numbers.firstOrNull { it !in onDevice && it !in skipped }
    }

    /** The archive puzzle numbers [technique]'s drill sources name, the most target steps first. */
    private fun candidates(technique: TechniqueId): List<Int> = game.drillSources
        .flatMap { it.candidates(technique) }
        .filterIsInstance<DrillCandidate.Archive>()
        .sortedByDescending { it.targetSteps }
        .map { it.number }
        .distinct()

    /** The puzzles [technique]'s drill sources bundle, the most target steps first: always on the device, so they never need a fetch. */
    private fun bundled(technique: TechniqueId): List<Puzzle> = game.drillSources
        .flatMap { it.candidates(technique) }
        .filterIsInstance<DrillCandidate.Bundled>()
        .sortedByDescending { it.targetSteps }
        .map { it.puzzle }
}
