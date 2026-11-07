package com.ivandsky.puzzlebox.play.friend

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the friend's puzzle route draws. */
internal sealed interface FriendCardState {
    data object Loading : FriendCardState

    /** The opened puzzle as its [card] shows it. */
    data class Ready(val card: FriendCard) : FriendCardState

    /** The code did not open; [outcome] says why. */
    data class Failed(val outcome: OpenOutcome) : FriendCardState
}

/**
 * What a friend's puzzle card shows: the stored [puzzle] in its game's [brand], named [gameName],
 * sized [size], and a preview of its [board].
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
) {
    /** The board the card's primary action opens. */
    val play: CustomGameKey get() = CustomGameKey(puzzle.gameId.value, puzzle.code.value)
}

/** The card's one primary action. */
internal sealed interface FriendCardAction {
    /** Never played here. */
    data object Play : FriendCardAction

    /** A play is saved at [elapsedMs]. */
    data class Resume(val elapsedMs: Long) : FriendCardAction

    /** Solved here, nothing saved: play it again. */
    data object Replay : FriendCardAction
}

/**
 * One [FriendPuzzleKey]'s card: opens the code with [LoadSharedPuzzle] (a puzzle already
 * on this device opens without the network), then follows the puzzle's saved play and its
 * completions, so the primary action reads Resume while a play is saved and Replay once a
 * solve is recorded. The card previews the puzzle's board through [FriendPuzzleBoard]: its top
 * rows when signed in, and signing in from the card ([signIn]) fills them in place. The game's
 * name, brand, size label and rank order come from the registries; the feature names no game.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FriendPuzzleViewModel(
    key: FriendPuzzleKey,
    private val load: LoadSharedPuzzle,
    private val completions: CompletionRepository,
    private val saved: SavedPlayRepository,
    private val games: GameRegistry,
    private val plays: GamePlayRegistry,
    private val editors: GameEditorRegistry,
    private val dispatchers: DispatcherProvider,
    private val board: FriendPuzzleBoard,
) : ViewModel() {

    private val opened = MutableStateFlow<OpenOutcome?>(null)

    val state: StateFlow<FriendCardState> = opened
        .flatMapLatest { outcome ->
            when (outcome) {
                null -> flowOf(FriendCardState.Loading)
                is OpenOutcome.Opened -> cardOf(outcome.puzzle).map(FriendCardState::Ready)
                else -> flowOf(FriendCardState.Failed(outcome))
            }
        }
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

    /** The preview's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch { board.signIn(activityContext) }
    }

    private fun open() {
        opening?.cancel()
        opening = viewModelScope.launch {
            opened.value = if (code == null) OpenOutcome.NotFound else withContext(dispatchers.default) { load(code) }
        }
    }

    private fun cardOf(puzzle: ReceivedPuzzle): Flow<FriendCard> {
        val slot = PlaySlot.Custom(puzzle.gameId, puzzle.code.value)
        val savedElapsed = saved.observeInProgress().map { it[slot] }.distinctUntilChanged()
        val order = games[puzzle.gameId]?.score?.rankOrder ?: RankOrder.TimeThenHints
        return combine(
            completions.observeCustom(puzzle.gameId, puzzle.code.value),
            savedElapsed,
            board.observe(puzzle, order, PREVIEW_TOP, PREVIEW_READ),
            board.signInStatus,
        ) { records, elapsed, preview, signIn ->
            val first = firstSolve(records)
            FriendCard(
                puzzle = puzzle,
                gameName = games.displayName(puzzle.gameId),
                brand = plays.brandOf(puzzle.gameId),
                size = editors[puzzle.gameId]?.sizeLabel(puzzle.variant) ?: puzzle.variant.value,
                action = when {
                    elapsed != null -> FriendCardAction.Resume(elapsed)
                    first != null -> FriendCardAction.Replay
                    else -> FriendCardAction.Play
                },
                yours = first?.let { BestScore(it.time, it.hints) },
                board = preview,
                signIn = signIn,
            )
        }
    }

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
