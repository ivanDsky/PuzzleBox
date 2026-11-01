package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

    override fun observe(board: LeaderboardRef): Flow<LeaderboardSnapshot?> {
        // A7: signed out = no board, no error — callers show their signed-out state.
        if (currentUid() == null) return flowOf(null)
        return store.observeEntries(board.boardId)
            .map<List<Map<String, Any?>>, LeaderboardSnapshot?> { docs ->
                LeaderboardSnapshot(board = board, entries = docs.mapNotNull(LeaderboardDocCodec::decode))
            }
            .catch { emit(null) } // listener error (offline, denied) degrades to null (A7)
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
