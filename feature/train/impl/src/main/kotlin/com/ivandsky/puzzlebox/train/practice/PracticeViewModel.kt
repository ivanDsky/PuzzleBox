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
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A game's practice, a technique's chapter at a time (spec §2.2): four focus boards then one review board (the catalog's
 * first technique has the focus boards only). Each board is a [PracticeBoard] cut from a puzzle on the device, newest
 * first (the [source]'s, then the technique's bundled ones), played with the game's own gestures until it says
 * [PracticeStatus.Done]. A done board is held for [BoardEndHoldMillis] ([hold], [skip]), then the next board opens on
 * its own; after the chapter's last the state is [PracticeState.Result], the chapter's tally against the technique's
 * mastery. The next chapter ([nextChapter]) and a fresh run of this one ([practiceAgain]) open only from that page.
 * Boards live in memory only: turning the device keeps this view model and so the board, while a new attempt or a
 * restore starts again at the opened technique's first board (A6, A15). What a board judges is recorded through
 * [recordAnswer] as it happens, one verdict per trained turn, so the in-a-row count outlives the board ([progress]);
 * no puzzle is completed, saved or replayed, and nothing but the trainer's own progress is written.
 *
 * With nothing on the device the state is [PracticeState.Empty] when the technique's drill sources name no archive
 * puzzle, else [PracticeState.NeedsConnection]. [tryAgain] asks [archive] to load the first named puzzle not on the
 * device, one fetch at most, and opens a board on it. A load that gives no board leaves [PracticeState.CantLoad] naming
 * a puzzle already on the device, which [tryAnother] opens, or [PracticeState.NeedsConnection] when there is none. A
 * puzzle that is gone, untrusted or never has the technique as its easiest move is not asked for again until every
 * named puzzle has been tried; a busy or offline load asks for the same puzzle next tap. No load is ever an answer.
 * Each board is cut from the puzzles not played in this chapter first, then one fetch, then the played ones again.
 */
internal class PracticeViewModel(
    private val game: TrainerGame,
    technique: TechniqueId,
    private val source: TrainerPuzzleSource,
    private val archive: TrainerArchive,
    private val store: TrainerProgressRepository,
    private val recordAnswer: RecordAnswer,
    private val dispatchers: DispatcherProvider,
    screenReader: ScreenReaderCheck,
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

    /** The run through the chapter now, tallied for its result; only touched on the main thread. */
    private var run = ChapterRun(chapterKinds(opened).size)

    private val endHold = BoardEndHold(screenReader)

    /** The puzzles a board was opened on in this chapter, so the next board is another one; only touched on the main thread. */
    private val played = mutableSetOf<Puzzle>()

    val state: StateFlow<PracticeState> = mutableState.asStateFlow()

    /** The technique and board practice is on, which the title row shows. */
    val chapter: StateFlow<PracticeChapter> = mutableChapter.asStateFlow()

    /** The finished board's hold, null while no board has ended. */
    val hold: StateFlow<BoardHold?> = endHold.hold

    /** Skips the rest of the hold (and with a screen reader on, ends it): the Done band's button. */
    fun skip() = endHold.skip()

    /** The app is in the foreground or not; the hold only counts down while it is. */
    fun setForeground(foreground: Boolean) = endHold.setForeground(foreground)

    /** Trained turns and queued work in the order the board reported them, so a Wrong never lands before the Right it followed. */
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch { for (work in queue) work() }
    }

    /** The stored progress of the technique being trained, which every trained turn changes and which outlives the board. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val progress: StateFlow<TechniqueProgress> = mutableChapter.map { it.technique.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> store.observe(game.gameId).map { it[id] ?: TechniqueProgress.NEW } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TechniqueProgress.NEW)

    /** Opens the first board for [attempt]. The same number again (the screen turned) leaves the board; a new one starts the opened technique's chapter over. */
    fun start(attempt: Int) {
        if (attempt == this.attempt) return
        this.attempt = attempt
        moveTo(opened, 0)
        replaceBoard {
            if (!openFromDevice()) mutableState.value = withContext(dispatchers.default) { nothingOnDevice(current) }
        }
    }

    /** The needs-a-connection tap: loads the first named puzzle not on the device. Ignored in any other state, so a second tap while loading changes nothing. */
    fun tryAgain() {
        if (mutableState.value != PracticeState.NeedsConnection) return
        replaceBoard {
            val technique = current
            val numbers = withContext(dispatchers.default) { game.archiveNumbers(technique) }
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

    private val current: TechniqueId get() = catalog[techniqueIndex].id

    private fun currentChapter() = PracticeChapter(catalog[techniqueIndex], boardIndex + 1, chapterKinds(techniqueIndex).size)

    /** Puts practice at board [board] (from 0) of the chapter of the technique at [index]; a technique that changes starts over on which puzzles failed and which were played, and the first board starts a new run. */
    private fun moveTo(index: Int, board: Int) {
        if (index != techniqueIndex) skipped.clear()
        if (index != techniqueIndex || board == 0) played.clear()
        if (board == 0) run = ChapterRun(chapterKinds(index).size)
        techniqueIndex = index
        boardIndex = board
        mutableChapter.value = currentChapter()
    }

    /** A finished board goes on: the chapter's next board, or the chapter's result after the last. */
    private fun advance() {
        if (boardIndex < chapterKinds(techniqueIndex).lastIndex) {
            moveTo(techniqueIndex, boardIndex + 1)
            replaceBoard { openNext() }
        } else {
            showResult()
        }
    }

    /** The result page's next-chapter button: the next technique's first board. Ignored anywhere else, and after the catalog's last technique. */
    fun nextChapter() {
        if ((mutableState.value as? PracticeState.Result)?.result?.next == null) return
        moveTo(techniqueIndex + 1, 0)
        replaceBoard { openNext() }
    }

    /** The result page's practice-again button: the same technique's chapter from its first board, tallied anew. Ignored anywhere else. */
    fun practiceAgain() {
        if (mutableState.value !is PracticeState.Result) return
        moveTo(techniqueIndex, 0)
        replaceBoard { openNext() }
    }

    /** Opens the board the chapter is at: a puzzle not played yet from the device, else one fetch, else the played ones round again. */
    private suspend fun openNext() {
        if (openFromDevice()) return
        val target = nextToFetch(withContext(dispatchers.default) { game.archiveNumbers(current) })
        if (target != null) {
            resolve(archive.load(game.gameId, current, target))
            return
        }
        played.clear()
        if (!openFromDevice()) mutableState.value = withContext(dispatchers.default) { nothingOnDevice(current) }
    }

    /** Ends the chapter: waits for every verdict to be recorded, then shows the tally against the technique's stored mark. */
    private fun showResult() {
        val run = run
        val index = techniqueIndex
        replaceBoard(loading = false) {
            val recorded = CompletableDeferred<Unit>()
            queue.send { recorded.complete(Unit) }
            recorded.await()
            mutableState.value = PracticeState.Result(run.result(catalog, index, store.observe(game.gameId).first()))
        }
    }

    /** Drops the live board and its scope, shows [PracticeState.Loading] unless [loading] is false, and runs [work], which ends in a new state. Any earlier work is cancelled. */
    private fun replaceBoard(loading: Boolean = true, work: suspend () -> Unit) {
        load?.cancel()
        boardLife?.cancel()
        boardLife = null
        endHold.clear()
        if (loading) mutableState.value = PracticeState.Loading
        load = viewModelScope.launch { work() }
    }

    /** One trained turn's verdict, queued to be tallied and recorded in the order it came, for as long as the view model lives, whatever becomes of the board. */
    private fun record(run: ChapterRun, board: Int, technique: TechniqueId, verdict: TrainerVerdict) {
        queue.trySend {
            run.verdict(board, verdict)
            recordAnswer(game.gameId, technique, verdict)
        }
    }

    /** A loaded puzzle opens a board; any other outcome, or a puzzle that never has the technique first, is a failed load. */
    private suspend fun resolve(load: ArchiveLoad) {
        if (load is ArchiveLoad.Loaded && open(load.puzzle)) return
        // Busy and offline are worth the same puzzle again; every other outcome is not.
        val retryable = load is ArchiveLoad.CantLoad &&
            (load.reason == ArchiveLoad.CantLoad.Reason.Busy || load.reason == ArchiveLoad.CantLoad.Reason.Offline)
        if (!retryable) skipped += load.number
        val technique = current
        val numbers = withContext(dispatchers.default) { game.archiveNumbers(technique) }
        val alternative = archive.onDevice(game.gameId, technique, numbers).firstOrNull { it !in skipped }
        mutableState.value = if (alternative == null) PracticeState.NeedsConnection else PracticeState.CantLoad(alternative)
    }

    /** Opens a board on the first puzzle on the device not played yet that has one: the source's, newest first, then the technique's bundled ones. False when none. */
    private suspend fun openFromDevice(): Boolean =
        source.puzzles(game.gameId).firstOrNull { openUnplayed(it) } != null ||
            withContext(dispatchers.default) { game.bundledPuzzles(current) }.any { openUnplayed(it) }

    private suspend fun openUnplayed(puzzle: Puzzle): Boolean = puzzle !in played && open(puzzle)

    /** Cuts a board of the current kind from [puzzle] off the main thread (the walk is CPU work) and shows it; false when [puzzle] never has the technique as its easiest move. */
    private suspend fun open(puzzle: Puzzle): Boolean {
        val parent = viewModelScope.coroutineContext
        val life = CoroutineScope(parent + SupervisorJob(parent[Job]))
        val technique = current
        val kind = chapterKinds(techniqueIndex)[boardIndex]
        val run = run
        val index = boardIndex
        if (run.wasMastered == null) run.wasMastered = store.observe(game.gameId).first()[technique]?.mark == TechniqueProgress.Mark.Mastered
        var board: PracticeBoard? = null
        try {
            board = withContext(dispatchers.default) { game.practice(puzzle, technique, kind, life) { record(run, index, technique, it) } }
        } finally {
            if (board == null) life.cancel()
        }
        val live = board ?: return false
        played += puzzle
        boardLife = life
        mutableState.value = PracticeState.Practicing(live)
        life.launch {
            live.status.transformWhile { status ->
                emit(status)
                status !is PracticeStatus.Done
            }.collect { run.see(index, it) }
            endHold.await()
            if (boardLife === life) advance()
        }
        return true
    }

    /** What the player sees with no board to play for [technique]: nothing to fetch at all, or a fetch to ask for. */
    private fun nothingOnDevice(technique: TechniqueId): PracticeState =
        if (game.archiveNumbers(technique).isEmpty()) PracticeState.Empty else PracticeState.NeedsConnection

    /** The first of [numbers] that is neither on the device nor already found useless. */
    private suspend fun nextToFetch(numbers: List<Int>): Int? {
        val onDevice = archive.onDevice(game.gameId, current, numbers).toSet()
        return numbers.firstOrNull { it !in onDevice && it !in skipped }
    }
}
