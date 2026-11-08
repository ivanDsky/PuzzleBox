package com.ivandsky.puzzlebox.play.friend

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.board.BoardSignInHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** The puzzle's author as its board pins them: their test solve's time and hints. */
internal fun ReceivedPuzzle.authorPin() = AuthorPin(uid = authorUid, name = authorName, timeMs = authorTimeMs, hints = authorHints)

/**
 * A friend puzzle's board as one screen reads it: ranked for whoever is signed in and read
 * again when that changes, so signing in here ([signIn]) fills it in place. Board reads need
 * sign-in, so a signed-out user's board is never read: it is [CustomBoardView.SignedOut] at once.
 */
internal class FriendPuzzleBoard(
    private val boards: CustomBoardRepository,
    private val identity: IdentityService,
    private val idTokens: GoogleIdTokenProvider,
    private val compute: ComputeCustomBoard,
) {

    private val signInHolder = BoardSignInHolder(identity, idTokens)

    /** How this screen's last sign-in went. */
    val signInStatus: StateFlow<BoardSignIn> = signInHolder.status

    /**
     * [puzzle]'s board ranked by [order], pinning its author: the first [top] rows, read [read]
     * deep so this user's own row below them is found. Null while a signed-in read is in flight.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(puzzle: ReceivedPuzzle, order: RankOrder, top: Int, read: Int): Flow<CustomBoardView?> {
        val author = puzzle.authorPin()
        return identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(compute(null, null, author, top))
            } else {
                boards.observe(puzzle.gameId, puzzle.code, order, read)
                    .map<_, CustomBoardView?> { entries -> compute(entries, uid, author, top) }
                    .onStart { emit(null) }
            }
        }
    }

    /** The board's Google button: [activityContext] hosts Google's account picker, and is not kept. A try under way is not repeated. */
    suspend fun signIn(activityContext: Context) = signInHolder.signIn(activityContext)
}
