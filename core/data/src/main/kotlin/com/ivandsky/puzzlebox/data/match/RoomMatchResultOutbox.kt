package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.database.MatchResultDao
import com.ivandsky.puzzlebox.database.MatchResultEntity
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Room adapter of [MatchResultOutbox] over `match_results` ([dao]). A row whose code no longer
 * parses is skipped; a state name this version does not know reads as [ResultPostState.Pending].
 */
internal class RoomMatchResultOutbox(private val dao: MatchResultDao) : MatchResultOutbox {

    override suspend fun enqueue(code: ShareCode, result: LegResult) = dao.insertIfAbsent(
        MatchResultEntity(
            matchCode = code.value,
            legIndex = result.legIndex,
            uid = result.uid,
            timeMs = result.timeMs,
            hints = result.hints,
            completedAtMs = result.completedAtMs,
            gaveUp = result.gaveUp,
            postState = ResultPostState.Pending.name,
            postAttempts = 0,
        ),
    )

    override fun observe(code: ShareCode): Flow<List<OutboxResult>> = dao.observe(code.value).mapRows()

    override fun observePending(): Flow<List<OutboxResult>> = dao.observePending().mapRows()

    override fun observeAll(): Flow<List<OutboxResult>> = dao.observeAll().mapRows()

    override suspend fun update(
        code: ShareCode,
        legIndex: Int,
        uid: String,
        state: ResultPostState,
        attempts: Int,
    ) = dao.update(code.value, legIndex, uid, state.name, attempts)
}

private fun Flow<List<MatchResultEntity>>.mapRows(): Flow<List<OutboxResult>> =
    map { rows -> rows.mapNotNull { it.toOutboxResult() } }.distinctUntilChanged()

private fun MatchResultEntity.toOutboxResult(): OutboxResult? {
    val code = ShareCode.parse(matchCode) ?: return null
    return OutboxResult(
        code = code,
        result = LegResult(uid, legIndex, timeMs, hints, completedAtMs, gaveUp),
        state = ResultPostState.entries.firstOrNull { it.name == postState } ?: ResultPostState.Pending,
        attempts = postAttempts,
    )
}
