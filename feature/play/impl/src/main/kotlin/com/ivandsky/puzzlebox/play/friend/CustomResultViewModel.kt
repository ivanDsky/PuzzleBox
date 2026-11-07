package com.ivandsky.puzzlebox.play.friend

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a friend's puzzle result draws: this solve's facts, the stored [puzzle], the solve's [standing] and the puzzle's [board]. */
internal data class CustomResultState(
    val timeMs: Long,
    val hintsUsed: Int,
    /** "Queens 8×8 · Crown the castle"; the game's name alone until the puzzle is read. */
    val title: String,
    val brand: GameBrand,
    val rankOrder: RankOrder,
    /** The friend's puzzle as stored when its code opened; null until read. */
    val puzzle: ReceivedPuzzle? = null,
    /** Null while the puzzle has no completion here yet: the first solve's write is in flight. */
    val standing: CustomStanding? = null,
    /** The puzzle's board; null until its first read lands. */
    val board: CustomBoardView? = null,
    val signIn: BoardSignIn = BoardSignIn.Idle,
)

/** Whether a solve set this device's record of a friend's puzzle. */
internal sealed interface CustomStanding {
    /** The first solve here, or one that beat the record: it is the record. */
    data object Record : CustomStanding

    /** A later solve that left [record] standing. */
    data class Replay(val record: BestScore) : CustomStanding
}

/** Where the board's sign-in stands; a cancelled sign-in is back to [Idle], a choice and not a failure. */
internal enum class BoardSignIn {
    Idle,
    SigningIn,

    /** Google or the account backend could not be reached. */
    Offline,

    /** Refused or unusable: worth trying again. */
    Failed,
}

/** The puzzle's author as its board pins them: their test solve's time and hints. */
internal fun ReceivedPuzzle.authorPin() = AuthorPin(uid = authorUid, name = authorName, timeMs = authorTimeMs, hints = authorHints)

/**
 * One [CustomResultKey]'s result: the solve's own facts ride the key, so the page draws at
 * once and after process death; the stored puzzle and this device's completions of it follow.
 * The solve is the Record when it is the row that stands on the board — the record row, else
 * the oldest — and a Replay otherwise, naming that row's score. The game's name, size, brand
 * and rank order come from the registries; the feature names no game.
 *
 * The puzzle's board is read for whoever is signed in and read again when that changes, so
 * signing in from the page ([signIn]) fills it in place. The read reaches past the shown top
 * rows, so this user's own row below them is found.
 */
internal class CustomResultViewModel(
    private val key: CustomResultKey,
    completions: CompletionRepository,
    received: ReceivedPuzzleRepository,
    games: GameRegistry,
    plays: GamePlayRegistry,
    editors: GameEditorRegistry,
    private val store: PlaySessionStore,
    private val boards: CustomBoardRepository,
    private val identity: IdentityService,
    private val idTokens: GoogleIdTokenProvider,
    private val computeBoard: ComputeCustomBoard,
) : ViewModel() {

    private val slot = PlaySlot.Custom(GameId(key.gameId), key.puzzleId)

    private val gameName = games.displayName(slot.gameId)

    private val solve = CustomResultState(
        timeMs = key.timeMs,
        hintsUsed = key.hintsUsed,
        title = gameName,
        brand = plays.brandOf(slot.gameId),
        rankOrder = games[slot.gameId]?.score?.rankOrder ?: RankOrder.TimeThenHints,
    )

    private val code = ShareCode.parse(key.puzzleId)

    private val puzzle: Flow<ReceivedPuzzle?> = code?.let(received::observe) ?: flowOf(null)

    private val signInState = MutableStateFlow(BoardSignIn.Idle)

    private val records = completions.observeCustom(slot.gameId, key.puzzleId)

    val state: StateFlow<CustomResultState> = combine(puzzle, records, board(), signInState) { puzzle, records, board, signIn ->
        solve.copy(
            title = puzzle?.let {
                val size = editors[it.gameId]?.sizeLabel(it.variant) ?: it.variant.value
                "$gameName $size · ${it.title}"
            } ?: gameName,
            puzzle = puzzle,
            standing = standingOf(records),
            board = board,
            signIn = signIn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), solve)

    /**
     * Play again: resets the finished live play, if it is still held, before its board
     * reopens — a board reopened on a complete play would hand the old solve off again.
     */
    fun playAgain() {
        store.peek(slot)?.takeIf { it.controller.isComplete }?.replay()
    }

    /** The board's Google button: [activityContext] hosts Google's account picker, and is not kept. */
    fun signIn(activityContext: Context) {
        if (signInState.value == BoardSignIn.SigningIn) return
        signInState.value = BoardSignIn.SigningIn
        viewModelScope.launch {
            signInState.value = when (val token = idTokens.idToken(activityContext)) {
                is GoogleIdToken.Available -> when (val result = identity.signInWithGoogleIdToken(token.token)) {
                    is AuthResult.Ok -> BoardSignIn.Idle
                    is AuthResult.Err -> if (result.error is AuthError.Unavailable) BoardSignIn.Offline else BoardSignIn.Failed
                }
                is GoogleIdToken.Unavailable -> when (token.reason) {
                    GoogleIdToken.Unavailable.Reason.CANCELLED -> BoardSignIn.Idle
                    GoogleIdToken.Unavailable.Reason.NETWORK -> BoardSignIn.Offline
                    else -> BoardSignIn.Failed
                }
            }
        }
    }

    /** The board once the puzzle names its author, re-read from the start for each signed-in user. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun board(): Flow<CustomBoardView?> {
        val code = code ?: return flowOf(null)
        return puzzle
            .map { it?.authorPin() }
            .distinctUntilChanged()
            .flatMapLatest { author ->
                if (author == null) return@flatMapLatest flowOf(null)
                identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
                    boards.observe(slot.gameId, code, solve.rankOrder, BOARD_READ)
                        .map<_, CustomBoardView?> { entries -> computeBoard(entries, uid, author, BOARD_TOP) }
                        .onStart { emit(null) }
                }
            }
    }

    /** [records] newest first; the row that stands is the record row, else the oldest. */
    private fun standingOf(records: List<CompletionRecord>): CustomStanding? {
        val stands = records.firstOrNull { it.isRecord } ?: records.lastOrNull() ?: return null
        return if (stands.time == key.timeMs && stands.hints == key.hintsUsed) {
            CustomStanding.Record
        } else {
            CustomStanding.Replay(record = BestScore(stands.time, stands.hints))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Rows shown above the cut. */
        const val BOARD_TOP = 5

        /** Rows read: enough to find this user's own row well below the cut. */
        const val BOARD_READ = 50
    }
}
