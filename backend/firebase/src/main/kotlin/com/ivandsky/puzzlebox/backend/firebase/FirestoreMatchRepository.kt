package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.PostResult
import com.ivandsky.puzzlebox.data.match.PuzzleCodeCheck
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firestore implementation of [MatchRepository] over `matches`, `matches/{code}/results` and
 * `matchQuota` (ADR-0026). Create is create-if-absent, so a retry after a lost ack finds the
 * caller's own match and is [CreateResult.Created]. Every Firebase exception is mapped to a typed
 * result here; only [CancellationException] escapes.
 *
 * A post is one batch the rules judge on the server, and offline the SDK queues it and never
 * answers: after [postTimeout] it is [PostResult.Unavailable] while the write stays queued, and
 * its retry then meets the rules' write-once result as [PostResult.Duplicate]. The rules answer
 * a refused post only with `PERMISSION_DENIED`, so the reason is worked out afterwards.
 */
internal class FirestoreMatchRepository(
    private val store: MatchStore,
    private val currentUid: () -> String?,
    private val clock: () -> Instant = Instant::now,
    private val postTimeout: Duration = POST_TIMEOUT,
) : MatchRepository {

    override suspend fun puzzleCodeTaken(code: ShareCode): PuzzleCodeCheck {
        val puzzle = codeRead { store.codeTaken(code.value) }
        return if (puzzle == PuzzleCodeCheck.Free) codeRead { store.matchExists(code.value) } else puzzle
    }

    override suspend fun create(draft: MatchDraft): CreateResult {
        val uid = currentUid() ?: return CreateResult.SignedOut
        if (draft.challenger.uid != uid) return CreateResult.Rejected("the challenger is not the signed-in user")
        // Only a puzzle blocks the code here: an existing match of the caller's own is a retry.
        when (val puzzle = codeRead { store.codeTaken(draft.code.value) }) {
            PuzzleCodeCheck.Taken -> return CreateResult.CodeTaken
            is PuzzleCodeCheck.Unavailable ->
                return if (puzzle.offline) CreateResult.Offline else CreateResult.Rejected(puzzle.message)
            PuzzleCodeCheck.Free -> Unit
        }
        return attempt(CreateResult.Offline, CreateResult::Rejected) {
            val fields = MatchDocCodec.encodeCreate(draft)
            when (val write = store.create(draft.code.value, fields, uid, utcDay())) {
                MatchCreate.Created -> CreateResult.Created(draft.code)
                is MatchCreate.Exists ->
                    if (write.challengerUid == uid) CreateResult.Created(draft.code) else CreateResult.CodeTaken

                MatchCreate.CapReached -> CreateResult.DailyCapReached
            }
        }
    }

    override suspend fun claim(code: ShareCode, me: Player): ClaimResult {
        val uid = currentUid() ?: return ClaimResult.SignedOut
        if (me.uid != uid) return ClaimResult.Rejected("the claimant is not the signed-in user")
        return attempt(ClaimResult.Offline, ClaimResult::Rejected) {
            when (store.claim(code.value, MatchDocCodec.encodeClaim(me), uid, clock())) {
                SeatWrite.Claimed, SeatWrite.Done -> ClaimResult.Claimed
                SeatWrite.Taken -> ClaimResult.Taken
                SeatWrite.Cancelled -> ClaimResult.Cancelled
                SeatWrite.Closed -> ClaimResult.Closed
                SeatWrite.NotFound -> ClaimResult.Rejected("no match ${code.value}")
            }
        }
    }

    override suspend fun cancel(code: ShareCode): WriteResult = seat(code) { uid -> store.cancel(code.value, uid) }

    override suspend fun decline(code: ShareCode): WriteResult = seat(code) { uid -> store.decline(code.value, uid) }

    private suspend fun seat(code: ShareCode, write: suspend (uid: String) -> SeatWrite): WriteResult {
        val uid = currentUid() ?: return WriteResult.Rejected("signed out")
        return attempt(WriteResult.Offline, WriteResult::Rejected) {
            when (write(uid)) {
                SeatWrite.NotFound -> WriteResult.Rejected("no match ${code.value}")
                else -> WriteResult.Done
            }
        }
    }

    override fun observe(code: ShareCode): Flow<MatchFetch> = store.observe(code.value)
        .map { snapshot ->
            when (snapshot) {
                is MatchSnapshot.Doc -> MatchDocCodec.decode(code, snapshot.fields)
                MatchSnapshot.Absent -> MatchFetch.NotFound
                MatchSnapshot.Unconfirmed -> MatchFetch.Offline
            }
        }
        .catch { emit(MatchFetch.Offline) }
        .distinctUntilChanged()

    /** The signed-in user is read when collection starts; a failed listener ends as null, like the boards. */
    override fun observeMine(): Flow<List<Match>?> = flow {
        val uid = currentUid()
        if (uid == null) {
            emit(null)
        } else {
            emitAll(store.observeMine(uid).map { docs -> docs.newestFirst() })
        }
    }.catch { emit(null) }.distinctUntilChanged()

    private fun Map<String, Map<String, Any?>>.newestFirst(): List<Match> = mapNotNull { (id, fields) ->
        ShareCode.parse(id)?.let { MatchDocCodec.decode(it, fields) as? MatchFetch.Found }?.match
    }.sortedWith(compareByDescending<Match> { it.createdAt }.thenBy { it.code.value })

    override suspend fun post(code: ShareCode, result: LegResult): PostResult {
        val uid = currentUid() ?: return PostResult.Rejected("signed out")
        if (result.uid != uid) return PostResult.Rejected("the result is not the signed-in user's")
        return try {
            val acknowledged = withTimeoutOrNull(postTimeout) {
                store.post(code.value, MatchDocCodec.encodeResult(result), uid, result.legIndex)
            }
            if (acknowledged == null) PostResult.Unavailable("no answer in $postTimeout") else PostResult.Posted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                e.isTransient() -> PostResult.Unavailable(e.message)
                e.isDenied() -> refused(code, result, e)
                else -> PostResult.Rejected(e.message)
            }
        }
    }

    /** Why the rules refused [result]: it is already there, it is too late, or it is simply refused. */
    private suspend fun refused(code: ShareCode, result: LegResult, cause: Exception): PostResult {
        if (orNull { store.readResult(code.value, result.legIndex, result.uid) } != null) return PostResult.Duplicate
        val match = (orNull { store.observe(code.value).first() } as? MatchSnapshot.Doc)
            ?.let { MatchDocCodec.decode(code, it.fields) as? MatchFetch.Found }?.match
        val late = match != null && (
            !clock().isBefore(match.closesAt.minusSeconds(CLOSE_MARGIN_SECONDS)) ||
                result.completedAtMs > match.deadline.toEpochMilli()
            )
        return if (late) PostResult.Late else PostResult.Rejected(cause.message)
    }

    // A refused read is the rules sealing the opponent's time; any other failure is worth a retry.
    override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead = try {
        val fields = store.readResult(code.value, legIndex, uid)
        fields?.let { MatchDocCodec.decodeResult(it) }?.let { ResultRead.Found(it) } ?: ResultRead.Missing
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.isDenied()) ResultRead.Sealed else ResultRead.Offline
    }

    /** [read] as a [PuzzleCodeCheck]: a failure a retry can fix is offline, any other is a refusal that will repeat. */
    private suspend fun codeRead(read: suspend () -> Boolean): PuzzleCodeCheck = try {
        if (read()) PuzzleCodeCheck.Taken else PuzzleCodeCheck.Free
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PuzzleCodeCheck.Unavailable(offline = e.isTransient(), message = e.message)
    }

    private fun utcDay(): String = clock().atZone(ZoneOffset.UTC).toLocalDate().toString()

    /** [block]'s result, or [offline] for a failure a retry can fix and [rejected] for the rest. */
    private suspend fun <R> attempt(offline: R, rejected: (String?) -> R, block: suspend () -> R): R = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.isTransient()) offline else rejected(e.message)
    }

    /** A read that answers null for any failure, which the caller takes as "could not tell". */
    private suspend fun <T> orNull(read: suspend () -> T): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private companion object {
        /** How long a post waits for the server before it stays queued and is [PostResult.Unavailable]. */
        val POST_TIMEOUT: Duration = 10.seconds

        /** I5: a refusal within this long of the close is [PostResult.Late] on the device's clock. */
        const val CLOSE_MARGIN_SECONDS = 60L
    }
}
