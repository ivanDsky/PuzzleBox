package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.PostResult
import com.ivandsky.puzzlebox.data.match.PuzzleCodeCheck
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [SelfMatchRepository]. It passes the same contract test as the Room adapter, so it cannot drift.
 * [recordReplay] stands in for the completion store: the replay of a leg puzzle's first solve, null by default.
 */
class FakeSelfMatchRepository(
    private val recordReplay: suspend (LegRef) -> SolveReplay? = { null },
) : SelfMatchRepository {

    private class Held(val match: Match, val past: Map<Int, LegResult>, val mine: Map<Int, LegResult>) {
        fun view(): Match = match.copy(progress = mapOf(SelfSeats.PAST to match.legs.indices.toSet(), SelfSeats.ME to mine.keys))
    }

    private val store = MutableStateFlow<Map<ShareCode, Held>>(emptyMap())

    override suspend fun holds(code: ShareCode): Boolean = code in store.value

    override fun observeAll(): Flow<List<Match>> =
        store.map { held -> held.values.map { it.view() }.sortedByDescending { it.createdAt } }.distinctUntilChanged()

    override suspend fun save(match: Match, past: List<LegResult>) {
        val frozen = past.associate { it.legIndex to it.copy(uid = SelfSeats.PAST, gaveUp = false, replay = null) }
        store.update { it + (match.code to Held(match.asStored(), frozen, mine = emptyMap())) }
    }

    override suspend fun remove(code: ShareCode) {
        store.update { it - code }
    }

    override fun observe(code: ShareCode): Flow<MatchFetch> =
        store.map { held -> held[code]?.let { MatchFetch.Found(it.view()) } ?: MatchFetch.NotFound }.distinctUntilChanged()

    override fun observeMine(): Flow<List<Match>?> = flowOf(emptyList())

    override suspend fun post(code: ShareCode, result: LegResult): PostResult {
        var outcome: PostResult = PostResult.Posted
        store.update { held ->
            val duel = held[code]
            outcome = when {
                duel == null -> PostResult.Rejected("no self duel ${code.value}")
                result.legIndex !in duel.match.legs.indices -> PostResult.Rejected("no leg ${result.legIndex}")
                result.uid != SelfSeats.ME -> PostResult.Rejected("only my seat posts to a self duel")
                result.legIndex in duel.mine -> PostResult.Duplicate
                else -> PostResult.Posted
            }
            if (duel == null || outcome != PostResult.Posted) return@update held
            held + (code to Held(duel.match, duel.past, duel.mine + (result.legIndex to result.copy(replay = null))))
        }
        return outcome
    }

    override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
        val duel = store.value[code] ?: return ResultRead.Missing
        val leg = duel.match.legs.getOrNull(legIndex) ?: return ResultRead.Missing
        val result = when (uid) {
            SelfSeats.ME -> duel.mine[legIndex]
            SelfSeats.PAST -> duel.past[legIndex]?.copy(replay = recordReplay(leg.ref))
            else -> null
        }
        return result?.let(ResultRead::Found) ?: ResultRead.Missing
    }

    override suspend fun puzzleCodeTaken(code: ShareCode): PuzzleCodeCheck = PuzzleCodeCheck.Unavailable(offline = false, NOT_OFFERED)
    override suspend fun create(draft: MatchDraft): CreateResult = CreateResult.Rejected(NOT_OFFERED)
    override suspend fun claim(code: ShareCode, me: Player): ClaimResult = ClaimResult.Rejected(NOT_OFFERED)
    override suspend fun cancel(code: ShareCode): WriteResult = WriteResult.Rejected(NOT_OFFERED)
    override suspend fun decline(code: ShareCode): WriteResult = WriteResult.Rejected(NOT_OFFERED)

    /** The match as the Room adapter reads it back: only what its table keeps, the rest the self-duel constants. */
    private fun Match.asStored(): Match = copy(
        formatVersion = Match.FORMAT_VERSION,
        createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS),
        deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
        challenger = challenger.copy(uid = SelfSeats.ME),
        invitedUid = null,
        opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
        cancelled = false,
        declined = false,
        rematchOf = null,
    )

    private companion object {
        const val NOT_OFFERED = "a self duel is saved and removed on the device, never shared"
    }
}
