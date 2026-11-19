package com.ivandsky.puzzlebox.play.friend

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.play.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the friend's puzzle route draws. */
internal sealed interface FriendCardState {
    data object Loading : FriendCardState

    /** The opened puzzle as its [card] shows it, with the [report] side of the screen (sign-in prompt, notice). */
    data class Ready(val card: FriendCard, val report: FriendReportUi = FriendReportUi()) : FriendCardState

    /** The puzzle was reported and is hidden on this device: the card is closed and the screen says so, then leaves. */
    data object Reported : FriendCardState

    /** The code did not open; [outcome] says why. */
    data class Failed(val outcome: OpenOutcome) : FriendCardState
}

/**
 * What a friend's puzzle card shows: the stored [puzzle] in its game's [brand], named [gameName],
 * sized [size], and a preview of its [board], closed once the author takes the puzzle down.
 * The page draws its ⋮ with Report.
 */
internal data class FriendCard(
    val puzzle: ReceivedPuzzle,
    val gameName: String,
    val brand: GameBrand,
    val size: String,
    val action: FriendCardAction,
    /** This device's solve that stands on the board; null until one is recorded. */
    val yours: BestScore?,
    /** The board's top rows; null while a signed-in read is in flight. */
    val board: CustomBoardView?,
    val signIn: BoardSignIn,
    /** The puzzle's board before any move, from the givens alone; null when the game has none to give. */
    val givens: GivensBoard? = null,
) {
    /** The page draws the puzzle's board in its hidden slot (nav-rethink M12-8). */
    val hiddenBoard: Boolean get() = givens != null

    /** The board the card's primary action opens. */
    val play: CustomGameKey get() = CustomGameKey(puzzle.gameId.value, puzzle.code.value)

    /** The author took the puzzle down: it still plays here, but its board takes no new entries. */
    val takenDown: Boolean get() = puzzle.unpublished
}

/** The card's one primary action. */
internal sealed interface FriendCardAction {
    /** Never played here. */
    data object Play : FriendCardAction

    /** A play is saved at [elapsedMs]. */
    data class Resume(val elapsedMs: Long) : FriendCardAction

    /** Solved here, nothing saved: play it again. */
    data object PlayAgain : FriendCardAction
}

/**
 * One [FriendPuzzleKey]'s card: opens the code with [LoadSharedPuzzle] (a puzzle already
 * on this device opens without the network, then [SyncReceivedStatus] rechecks its doc, so a
 * puzzle its author took down reads taken down; offline it keeps its last known state), then
 * follows the stored row, the puzzle's saved play and its completions, so the primary action
 * reads Resume while a play is saved and Play again once a solve is recorded. The card previews the puzzle's board through [FriendPuzzleBoard]: its top
 * rows when signed in, and signing in from the card ([signIn]) fills them in place. The game's
 * name, brand, size label and rank order come from the registries; the feature names no game.
 *
 * Report ([report], nav-rethink C1) hides the puzzle on this device through [reportPuzzle] and turns the state to
 * [FriendCardState.Reported]; signed out it opens the sign-in prompt instead and the player taps Report again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FriendPuzzleViewModel(
    key: FriendPuzzleKey,
    private val load: LoadSharedPuzzle,
    private val sync: SyncReceivedStatus,
    private val received: ReceivedPuzzleRepository,
    private val completions: CompletionRepository,
    private val saved: SavedPlayRepository,
    private val games: GameRegistry,
    private val plays: GamePlayRegistry,
    private val editors: GameEditorRegistry,
    private val dispatchers: DispatcherProvider,
    private val board: FriendPuzzleBoard,
    private val reportPuzzle: ReportPuzzle,
) : ViewModel() {

    private val opened = MutableStateFlow<OpenOutcome?>(null)
    private val reported = MutableStateFlow(false)
    private val reportUi = MutableStateFlow(FriendReportUi())
    private var reporting = false
    private var noticeCount = 0

    val state: StateFlow<FriendCardState> = reported
        .flatMapLatest { done -> if (done) flowOf(FriendCardState.Reported) else openedState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FriendCardState.Loading)

    private val code = ShareCode.parse(key.code)
    private var opening: Job? = null

    init {
        open()
    }

    /** Opens the code again after it didn't open, showing Loading while it runs. */
    fun retry() {
        opened.value = null
        open()
    }

    /** The preview's and the sign-in prompt's Google button: [activityContext] hosts Google's account picker, and is not kept. Signed in, the prompt closes. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            board.signIn(activityContext)
            if (board.signedIn) reportUi.update { it.copy(signInPrompt = false) }
        }
    }

    /** Reports this puzzle. Reported (or already reported) it is hidden and the card closes; every other result hides nothing. */
    fun report() {
        val shared = code ?: return
        if (reporting) return
        reporting = true
        viewModelScope.launch {
            try {
                when (reportPuzzle(shared)) {
                    ReportResult.Reported, ReportResult.AlreadyReported -> reported.value = true
                    ReportResult.SignedOut -> reportUi.update { it.copy(signInPrompt = true) }
                    ReportResult.Offline -> say("You're offline. Report it when you're back.")
                    is ReportResult.Rejected -> say("Couldn't send the report. Try again later.")
                }
            } finally {
                reporting = false
            }
        }
    }

    fun dismissSignIn() = reportUi.update { it.copy(signInPrompt = false) }

    /** The snackbar for notice [id] has been and gone. */
    fun noticeShown(id: Int) = reportUi.update { if (it.notice?.id == id) it.copy(notice = null) else it }

    private fun say(message: String) = reportUi.update { it.copy(notice = FriendNotice(++noticeCount, message)) }

    private fun openedState(): Flow<FriendCardState> = opened.flatMapLatest { outcome ->
        when (outcome) {
            null -> flowOf(FriendCardState.Loading)
            is OpenOutcome.Opened -> cardOf(outcome.puzzle).combine(reportUi, FriendCardState::Ready)
            else -> flowOf(FriendCardState.Failed(outcome))
        }
    }

    private fun open() {
        opening?.cancel()
        opening = viewModelScope.launch {
            if (code == null) {
                opened.value = OpenOutcome.NotFound
                return@launch
            }
            // A fresh open has just read the doc; a stored one opened offline-first, so recheck it.
            val stored = received.get(code)
            opened.value = withContext(dispatchers.default) { load(code) }
            if (stored != null && !stored.unpublished) sync(code)
        }
    }

    private fun cardOf(puzzle: ReceivedPuzzle): Flow<FriendCard> {
        val slot = PlaySlot.Custom(puzzle.gameId, puzzle.code.value)
        val savedElapsed = saved.observeInProgress().map { it[slot] }.distinctUntilChanged()
        val order = games[puzzle.gameId]?.score?.rankOrder ?: RankOrder.TimeThenHints
        val current = received.observe(puzzle.code).map { it ?: puzzle }.distinctUntilChanged()
        val givens = givensOf(puzzle)
        return combine(
            current,
            completions.observeCustom(puzzle.gameId, puzzle.code.value),
            savedElapsed,
            board.observe(puzzle, order, PREVIEW_TOP, PREVIEW_READ),
            board.signInStatus,
        ) { row, records, elapsed, preview, signIn ->
            val first = firstSolve(records)
            FriendCard(
                puzzle = row,
                gameName = games.displayName(puzzle.gameId),
                brand = plays.brandOf(puzzle.gameId),
                size = editors[puzzle.gameId]?.sizeLabel(puzzle.variant) ?: puzzle.variant.value,
                action = when {
                    elapsed != null -> FriendCardAction.Resume(elapsed)
                    first != null -> FriendCardAction.PlayAgain
                    else -> FriendCardAction.Play
                },
                yours = first?.let { BestScore(it.time, it.hints) },
                board = preview,
                signIn = signIn,
                givens = givens,
            )
        }
    }

    /** [puzzle]'s board before any move, built by its game from the stored puzzle (the solution stays behind); null when the game has none to give. Built once per puzzle so the card's slot keeps its identity. */
    private fun givensOf(puzzle: ReceivedPuzzle): GivensBoard? =
        plays[puzzle.gameId]?.replayBoard?.givens(Puzzle(puzzle.gameId, puzzle.variant, puzzle.sharedFormatVersion, puzzle.puzzleBytes))

    /** The solve that stands on the board: the record row, else the oldest of [records] (newest first). */
    private fun firstSolve(records: List<CompletionRecord>): CompletionRecord? =
        records.firstOrNull { it.isRecord } ?: records.lastOrNull()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Rows the card previews. */
        const val PREVIEW_TOP = 3

        /** Rows read: one past the preview, so the author's own entry, never ranked, leaves three. */
        const val PREVIEW_READ = PREVIEW_TOP + 1
    }
}
