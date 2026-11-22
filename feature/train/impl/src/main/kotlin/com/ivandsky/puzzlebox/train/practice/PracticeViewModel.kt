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
 * first technique has the focus boards only), each a [PracticeBoard] cut from a puzzle on the device (the [source]'s,
 * newest first, then the technique's bundled ones; a focus board prefers one with two or more trained moves) and played until it says [PracticeStatus.Done]. A technique never
 * answered opens with its intro when it has one ([intros]; the board's chip replays it any time): demos to watch, then
 * [startPractice] opens board 1 on another puzzle than the demo's (the demo's own only when it is the one there is). A done board is held ([hold], [skip]), then the next opens on its own; after the last
 * the state is [PracticeState.Result]. The next chapter ([nextChapter]) and a fresh run ([practiceAgain]) open only from
 * that page. Boards live in memory only: turning the device keeps them, a new attempt starts the opened technique again
 * (A6, A15). Each trained turn is recorded through [recordAnswer] as it happens ([progress]); nothing but the
 * trainer's own progress is written.
 *
 * With nothing on the device the state is [PracticeState.Empty] when the technique's drill sources name no archive
 * puzzle, else [PracticeState.NeedsConnection]. [tryAgain] asks [archive] to load the first named puzzle not on the
 * device, one fetch at most. A load that gives no board leaves [PracticeState.CantLoad] naming a puzzle already on the
 * device, which [tryAnother] opens, or [PracticeState.NeedsConnection] when there is none. A puzzle that is gone,
 * untrusted or never has the technique as its easiest move is not asked for again until every named one has been tried;
 * a busy or offline load asks for the same puzzle next tap. Each board is cut from the puzzles not played in this
 * chapter first, then one fetch, then the played ones again.
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
    hint: IntroHint,
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
    private val trainedMoves = TrainedMovesCache()

    val state: StateFlow<PracticeState> = mutableState.asStateFlow()

    /** The technique and board practice is on, which the title row shows. */
    val chapter: StateFlow<PracticeChapter> = mutableChapter.asStateFlow()

    /** The finished board's hold, null while no board has ended. */
    val hold: StateFlow<BoardHold?> = endHold.hold

    /** The technique's intro and its replay from the board's chip. */
    val intros = IntroGate(game, source, dispatchers, hint)

    /** Skips the rest of the hold (and with a screen reader on, ends it): the Done band's button. */
    fun skip() = endHold.skip()

    /** The app is in the foreground or not; the hold only counts down while it is. */
    fun setForeground(foreground: Boolean) = endHold.setForeground(foreground)

    /** Trained turns and queued work in the order the board reported them, so a Wrong never lands before the Right it followed. */
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init { viewModelScope.launch { for (work in queue) work() } }

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
        replaceBoard { if (!introduce() && !openFromDevice()) mutableState.value = withContext(dispatchers.default) { game.nothingOnDevice(current) } }
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
        replaceBoard { if (!introduce()) openNext() }
    }

    /** The result page's practice-again button: the same technique's chapter from its first board, tallied anew. Ignored anywhere else. */
    fun practiceAgain() {
        if (mutableState.value !is PracticeState.Result) return
        moveTo(techniqueIndex, 0)
        replaceBoard { if (!introduce()) openNext() }
    }

    /** The intro's Skip and Start practice: board 1 opens on another puzzle than the intro was recorded on, which is already played; on that one when it is all there is. Ignored anywhere but the opening intro. */
    fun startPractice() {
        val first = (mutableState.value as? PracticeState.Intro)?.intro?.first ?: return
        intros.handedOver()
        replaceBoard(loading = false) { if (!openFromDevice() && !open(first)) openNext() }
    }

    /** Finds the chapter's intro for the board's chip and shows it first for a technique never answered (spec §2.7); false when not shown. */
    private suspend fun introduce(): Boolean {
        val intro = intros.find(current) ?: return false
        if ((store.observe(game.gameId).first()[current] ?: TechniqueProgress.NEW).mark != TechniqueProgress.Mark.New) return false
        played += intro.first
        mutableState.value = PracticeState.Intro(intro)
        return true
    }

    /** Opens the board the chapter is at: a puzzle not played yet from the device, else one fetch, else the played ones round again. */
    private suspend fun openNext() {
        if (openFromDevice()) return
        val target = nextToFetch(withContext(dispatchers.default) { game.archiveNumbers(current) })
        if (target != null) return resolve(archive.load(game.gameId, current, target))
        played.clear()
        if (!openFromDevice()) mutableState.value = withContext(dispatchers.default) { game.nothingOnDevice(current) }
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
        intros.close()
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

    /** Opens a board on a puzzle on the device not played yet ([openDevicePuzzle]): a focus board prefers one that trains several moves. False when none opens. */
    private suspend fun openFromDevice(): Boolean =
        game.openDevicePuzzle(source, current, played, dispatchers, preferLong = chapterKinds(techniqueIndex)[boardIndex] == BoardKind.Focus, counts = trainedMoves) { open(it) }

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

    private suspend fun nextToFetch(numbers: List<Int>): Int? = game.nextToFetch(archive, current, numbers, skipped)
}
