package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.game.RankOrder
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * Firestore implementation of the leaderboard seam (design §3): boards live at
 * `leaderboards/{boardId}/entries/{uid}` (see firestore.rules); submissions are
 * create-if-absent so the first write stands (A4); dedup key is (uid, puzzleRef) —
 * the doc id is the uid and the board id re-validates the puzzleRef's day.
 * Every Firebase exception is mapped to a typed error HERE; none cross the seam.
 */
internal class FirestoreLeaderboardRepository(
    private val store: LeaderboardStore,
    private val currentUid: () -> String?,
) : LeaderboardRepository {

    override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> {
        // A7: signed out = no board, no error — callers show their signed-out state.
        if (currentUid() == null) return flowOf(null)
        return store.observeEntries(board.boardId, order)
            .map<List<Map<String, Any?>>, LeaderboardSnapshot?> { docs ->
                LeaderboardSnapshot(board = board, entries = docs.mapNotNull(LeaderboardDocCodec::decode))
            }
            // Transient listener errors (offline flap) retry with backoff; only when
            // they persist does the flow terminate with the A7 soft null — one hiccup
            // must not kill the board for the whole session. TERMINAL null (not an
            // error) stays the contract so callers never surface a failure.
            .retryWhen { cause, attempt ->
                if (cause is Exception && attempt < MAX_OBSERVE_RETRIES) {
                    delay(OBSERVE_RETRY_BASE_MS * (attempt + 1))
                    true
                } else {
                    false
                }
            }
            .catch { emit(null) }
    }

    override suspend fun fetch(board: LeaderboardRef, order: RankOrder): BoardPage? {
        if (currentUid() == null) return null
        return softNull {
            val entries = store.fetchEntries(board.boardId, order).mapNotNull(LeaderboardDocCodec::decode)
            // Count only a page that may hide a longer board; a short page IS the board.
            val count = if (entries.size >= BoardPage.PAGE_SIZE) store.count(board.boardId).toInt() else entries.size
            BoardPage(entries, count)
        }
    }

    override suspend fun count(board: LeaderboardRef): Int? {
        if (currentUid() == null) return null
        return softNull { store.count(board.boardId).toInt() }
    }

    /** A7/A14: a failed one-shot read is a soft null, never an error across the seam. */
    private inline fun <T> softNull(read: () -> T): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override suspend fun submit(entry: LeaderboardEntry): SubmitResult {
        if (currentUid() == null) {
            return SubmitResult.Err(SubmitError.Unavailable("signed out — entry kept local only"))
        }
        val board = LeaderboardRef.fromPuzzleRefKey(entry.puzzleRef)
            ?: return SubmitResult.Err(
                SubmitError.Rejected("puzzleRef ${entry.puzzleRef} is not an official daily (A5)"),
            )
        return try {
            val created = store.createIfAbsent(
                boardId = board.boardId,
                uid = entry.uid,
                data = LeaderboardDocCodec.encode(entry),
            )
            if (created) SubmitResult.Ok(entry) else SubmitResult.Deduped(existing = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            SubmitResult.Err(SubmitError.Unavailable(e.message))
        } catch (e: FirebaseFirestoreException) {
            SubmitResult.Err(e.toSubmitError())
        } catch (e: Exception) {
            SubmitResult.Err(SubmitError.Unknown(e))
        }
    }

    private companion object {
        const val MAX_OBSERVE_RETRIES = 2
        const val OBSERVE_RETRY_BASE_MS = 1_000L
    }
}

internal fun FirebaseFirestoreException.toSubmitError(): SubmitError = when (code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED,
    FirebaseFirestoreException.Code.UNAUTHENTICATED,
    -> SubmitError.Rejected(message)

    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
    FirebaseFirestoreException.Code.FAILED_PRECONDITION,
    -> SubmitError.Rejected(message)

    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.ABORTED,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
    FirebaseFirestoreException.Code.INTERNAL,
    -> SubmitError.Unavailable(message)

    else -> SubmitError.Unknown(this)
}
