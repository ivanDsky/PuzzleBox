package com.ivandsky.puzzlebox.data.match

import android.util.Log
import com.ivandsky.puzzlebox.model.ShareCode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The app's [MatchRepository]: a duel against your own first solves lives in [self], every other duel in
 * [remote], and no caller has to know which. Reading a duel and its results ([observe], [post], [readResult])
 * goes to [self] when it holds the code, else to [remote]. Everything else is a friend duel's lifecycle or the
 * signed-in user's list, which a self duel never has, so it goes to [remote] whatever the code.
 *
 * The store is chosen once per call; an open [observe] stays on the store it started on.
 */
class RoutingMatchRepository(
    private val self: SelfMatchRepository,
    private val remote: MatchRepository,
) : MatchRepository {

    override suspend fun puzzleCodeTaken(code: ShareCode): PuzzleCodeCheck = remote.puzzleCodeTaken(code)

    override suspend fun create(draft: MatchDraft): CreateResult = remote.create(draft)

    override suspend fun claim(code: ShareCode, me: Player): ClaimResult = remote.claim(code, me)

    override suspend fun cancel(code: ShareCode): WriteResult = remote.cancel(code)

    override suspend fun decline(code: ShareCode): WriteResult = remote.decline(code)

    override fun observe(code: ShareCode): Flow<MatchFetch> = flow { emitAll(storeFor(code).observe(code)) }

    override fun observeMine(): Flow<List<Match>?> = remote.observeMine()

    override suspend fun post(code: ShareCode, result: LegResult): PostResult = storeFor(code).post(code, result)

    override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead =
        storeFor(code).readResult(code, legIndex, uid)

    /** A lookup that fails picks [remote]: a broken device store must never take friend duels down with it. */
    private suspend fun storeFor(code: ShareCode): MatchRepository = try {
        if (self.holds(code)) self else remote
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "self duel lookup failed, using the remote: ${e.message}")
        remote
    }

    private companion object {
        const val TAG = "RoutingMatchRepository"
    }
}
