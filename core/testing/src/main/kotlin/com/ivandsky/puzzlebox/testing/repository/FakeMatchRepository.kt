package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.DAILY_MATCH_CAP
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
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * In-memory [MatchRepository] with the semantics of the Firestore rules for duels, so it passes the same contract as
 * the Firestore adapter: create-if-absent with a per-challenger, per-UTC-day quota of [DAILY_MATCH_CAP]; a set-once
 * opponent seat; cancel by the challenger while unclaimed and decline by the invited player, both idempotent; one
 * result per leg and player, posted before the close and completed by the deadline; and the other player's result
 * sealed until the reader posted that leg or the match closed. Knobs: [signedInUid], [online] and [now]; every open
 * flow follows them and every write.
 */
class FakeMatchRepository : MatchRepository {

    private data class Stored(val match: Match, val participants: Set<String>)
    private data class ResultKey(val code: ShareCode, val legIndex: Int, val uid: String)
    private data class Quota(val day: LocalDate, val count: Int)

    private val lock = Any()
    private val stored = MutableStateFlow<Map<ShareCode, Stored>>(emptyMap())
    private val results = HashMap<ResultKey, LegResult>()
    private val quotas = HashMap<String, Quota>()
    private val occupied = HashSet<ShareCode>()
    private val uid = MutableStateFlow<String?>(null)
    private val connected = MutableStateFlow(true)

    /** Every stored match by code, as last written. */
    val matches: Map<ShareCode, Match> get() = stored.value.mapValues { it.value.match }

    /** The signed-in user; null = signed out. */
    var signedInUid: String?
        get() = uid.value
        set(value) {
            uid.value = value
        }

    /** False makes every call Offline (a post Unavailable) and writes nothing. */
    var online: Boolean
        get() = connected.value
        set(value) {
            connected.value = value
        }

    /** The server clock: a new match's `createdAt`, the quota day, and the time every deadline is compared with. */
    @Volatile var now: Instant = Instant.EPOCH

    /** Registers [code] as a published puzzle, which no match may take. */
    fun occupyPuzzleCode(code: ShareCode) {
        synchronized(lock) { occupied += code }
    }

    override suspend fun puzzleCodeTaken(code: ShareCode): PuzzleCodeCheck = synchronized(lock) {
        when {
            !online -> PuzzleCodeCheck.Unavailable(offline = true)
            code in stored.value || code in occupied -> PuzzleCodeCheck.Taken
            else -> PuzzleCodeCheck.Free
        }
    }

    override suspend fun create(draft: MatchDraft): CreateResult =
        write(CreateResult.Offline, CreateResult.SignedOut) { me ->
            val existing = stored.value[draft.code]?.match
            when {
                draft.challenger.uid != me -> CreateResult.Rejected("the challenger is not the signed-in user")
                existing != null && existing.challenger.uid == me -> CreateResult.Created(draft.code)
                existing != null || draft.code in occupied -> CreateResult.CodeTaken
                else -> malformation(draft)?.let { CreateResult.Rejected(it) } ?: insertWithinQuota(draft, me)
            }
        }

    override suspend fun claim(code: ShareCode, me: Player): ClaimResult =
        write(ClaimResult.Offline, ClaimResult.SignedOut) { caller ->
            val entry = stored.value[code] ?: return@write ClaimResult.Rejected("no match ${code.value}")
            val match = entry.match
            when {
                me.uid != caller -> ClaimResult.Rejected("the claimant is not the signed-in user")
                match.opponent?.uid == caller -> ClaimResult.Claimed
                match.challenger.uid == caller -> ClaimResult.Rejected("the challenger cannot claim their own match")
                match.opponent != null -> ClaimResult.Taken
                match.cancelled || match.declined -> ClaimResult.Cancelled
                now >= match.deadline -> ClaimResult.Closed
                match.invitedUid != null && match.invitedUid != caller -> ClaimResult.Rejected("invited someone else")
                else -> {
                    put(Stored(match.copy(opponent = me), entry.participants + caller))
                    ClaimResult.Claimed
                }
            }
        }

    override suspend fun cancel(code: ShareCode): WriteResult =
        write(WriteResult.Offline, WriteResult.Rejected("signed out")) { caller ->
            val entry = stored.value[code] ?: return@write WriteResult.Rejected("no match ${code.value}")
            val match = entry.match
            when {
                match.challenger.uid != caller -> WriteResult.Rejected("only the challenger cancels")
                match.cancelled -> WriteResult.Done
                match.opponent != null -> WriteResult.Rejected("an opponent has joined")
                else -> {
                    put(entry.copy(match = match.copy(cancelled = true)))
                    WriteResult.Done
                }
            }
        }

    override suspend fun decline(code: ShareCode): WriteResult =
        write(WriteResult.Offline, WriteResult.Rejected("signed out")) { caller ->
            val entry = stored.value[code] ?: return@write WriteResult.Rejected("no match ${code.value}")
            val match = entry.match
            when {
                match.invitedUid != caller -> WriteResult.Rejected("only the invited player declines")
                match.declined -> WriteResult.Done
                else -> {
                    put(entry.copy(match = match.copy(declined = true)))
                    WriteResult.Done
                }
            }
        }

    override fun observe(code: ShareCode): Flow<MatchFetch> =
        combine(stored, connected) { matches, isOnline ->
            when {
                !isOnline -> MatchFetch.Offline
                else -> matches[code]?.let { MatchFetch.Found(it.match) } ?: MatchFetch.NotFound
            }
        }.distinctUntilChanged()

    override fun observeMine(): Flow<List<Match>?> =
        combine(stored, uid) { matches, me ->
            me?.let { matches.values.filter { me in it.participants }.map { it.match }.sortedByDescending { it.createdAt } }
        }.distinctUntilChanged()

    override suspend fun post(code: ShareCode, result: LegResult): PostResult =
        write(PostResult.Unavailable("offline"), PostResult.Rejected("signed out")) { caller ->
            val entry = stored.value[code] ?: return@write PostResult.Rejected("no match ${code.value}")
            val match = entry.match
            val key = ResultKey(code, result.legIndex, result.uid)
            when {
                result.uid != caller -> PostResult.Rejected("the result is not the signed-in user's")
                caller != match.challenger.uid && caller != match.opponent?.uid -> PostResult.Rejected("not a seated player")
                match.cancelled || match.declined -> PostResult.Rejected("the match is cancelled or declined")
                result.legIndex !in match.legs.indices -> PostResult.Rejected("no leg ${result.legIndex}")
                key in results -> PostResult.Duplicate
                now >= match.closesAt || result.completedAtMs > match.deadline.toEpochMilli() -> PostResult.Late
                !result.isWellFormed() -> PostResult.Rejected("malformed result")
                else -> {
                    results[key] = result
                    val legs = match.progress[caller].orEmpty() + result.legIndex
                    put(entry.copy(match = match.copy(progress = match.progress + (caller to legs))))
                    PostResult.Posted
                }
            }
        }

    override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead = synchronized(lock) {
        if (!online) return ResultRead.Offline
        val entry = stored.value[code] ?: return ResultRead.Missing
        val result = results[ResultKey(code, legIndex, uid)] ?: return ResultRead.Missing
        val me = signedInUid
        val readable = me != null && (
            me == uid ||
                me in entry.participants && (ResultKey(code, legIndex, me) in results || now >= entry.match.closesAt)
            )
        if (readable) ResultRead.Found(result) else ResultRead.Sealed
    }

    /** One atomic write: [offline] while offline, [signedOut] while signed out, else [block] with the caller's uid. */
    private inline fun <R> write(offline: R, signedOut: R, block: (caller: String) -> R): R = synchronized(lock) {
        when {
            !online -> offline
            else -> signedInUid?.let(block) ?: signedOut
        }
    }

    private fun malformation(draft: MatchDraft): String? = when {
        draft.deadlineHours !in Match.DEADLINE_CHOICES -> "deadlineHours ${draft.deadlineHours}"
        draft.legs.size !in 1..Match.MAX_LEGS -> "${draft.legs.size} legs"
        draft.challenger.name.length !in 1..MAX_NAME_LENGTH -> "challenger name length ${draft.challenger.name.length}"
        else -> null
    }

    private fun insertWithinQuota(draft: MatchDraft, me: String): CreateResult {
        val day = now.atZone(ZoneOffset.UTC).toLocalDate()
        val used = quotas[me]?.takeIf { it.day == day }?.count ?: 0
        if (used >= DAILY_MATCH_CAP) return CreateResult.DailyCapReached
        quotas[me] = Quota(day, used + 1)
        val match = Match(
            code = draft.code,
            formatVersion = Match.FORMAT_VERSION,
            createdAt = now,
            deadlineHours = draft.deadlineHours,
            challenger = draft.challenger,
            invitedUid = draft.invitedUid,
            opponent = null,
            legs = draft.legs.toList(),
            includeFriends = draft.includeFriends,
            cancelled = false,
            declined = false,
            rematchOf = draft.rematchOf,
            progress = emptyMap(),
        )
        put(Stored(match, setOfNotNull(me, draft.invitedUid)))
        return CreateResult.Created(draft.code)
    }

    private fun put(entry: Stored) {
        stored.value = stored.value + (entry.match.code to entry)
    }

    private fun LegResult.isWellFormed() = hints >= 0 && if (gaveUp) timeMs == 0L else timeMs > 0

    private companion object {
        /** The rules' bound on `challengerName`. */
        const val MAX_NAME_LENGTH = 64
    }
}
