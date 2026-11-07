package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Firestore implementation of the per-puzzle board over `puzzles/{code}/entries/{uid}`. Entries
 * use the daily board's document format; submissions are create-if-absent so the first win
 * stands. Every Firebase exception is mapped to a typed result here.
 */
internal class FirestoreCustomBoardRepository(
    private val store: CustomBoardStore,
    private val currentUid: () -> String?,
) : CustomBoardRepository {

    override fun observe(
        gameId: GameId,
        code: ShareCode,
        order: RankOrder,
        limit: Int,
    ): Flow<List<LeaderboardEntry>?> {
        // Rules deny signed-out reads; answer without a round trip.
        if (currentUid() == null) return flowOf(null)
        val ref = customRef(gameId, code)
        return store.observeEntries(code.value, order.boardQueryFields(), limit.toLong())
            .map<List<Map<String, Any?>>, List<LeaderboardEntry>?> { docs ->
                docs.mapNotNull { LeaderboardDocCodec.decode(it) { key -> key == ref } }
            }
            .catch { emit(null) }
    }

    override suspend fun submit(code: ShareCode, entry: LeaderboardEntry): SubmitResult {
        if (currentUid() == null) {
            return SubmitResult.Err(SubmitError.Unavailable("signed out — entry kept local only"))
        }
        if (!entry.puzzleRef.isCustomRefOf(code)) {
            return SubmitResult.Err(SubmitError.Rejected("puzzleRef ${entry.puzzleRef} is not puzzle ${code.value}"))
        }
        return try {
            val existing = store.createIfAbsent(code.value, entry.uid, LeaderboardDocCodec.encode(entry))
            if (existing == null) {
                SubmitResult.Ok(entry)
            } else {
                SubmitResult.Deduped(LeaderboardDocCodec.decode(existing) { it.isCustomRefOf(code) })
            }
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

    override suspend fun solvesCount(code: ShareCode): Int? {
        if (currentUid() == null) return null
        return try {
            store.count(code.value).toInt()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

private fun customRef(gameId: GameId, code: ShareCode): String =
    "${PuzzleKey.Custom.NAMESPACE}/${gameId.value}/${code.value}"

private fun String.isCustomRefOf(code: ShareCode): Boolean {
    val parts = split('/')
    return parts.size == 3 && parts[0] == PuzzleKey.Custom.NAMESPACE && parts[1].isNotEmpty() && parts[2] == code.value
}
