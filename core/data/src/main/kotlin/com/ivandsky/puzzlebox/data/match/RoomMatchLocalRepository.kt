package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.database.MatchLocalDao
import com.ivandsky.puzzlebox.database.MatchLocalEntity
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Room adapter of [MatchLocalRepository] over `matches_local` ([dao]). Instants are epoch ms; a
 * role name this version does not know reads as [LocalRole.Invite]; a row whose code no longer
 * parses is skipped. [clock] stamps `openedAt` of new rows.
 */
internal class RoomMatchLocalRepository(
    private val dao: MatchLocalDao,
    private val clock: () -> Instant = Instant::now,
) : MatchLocalRepository {

    override fun observeAll(): Flow<List<LocalMatch>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toLocalMatch() } }.distinctUntilChanged()

    override suspend fun remember(code: ShareCode, role: LocalRole, opponentName: String?) {
        dao.insertIfAbsent(
            MatchLocalEntity(
                code = code.value,
                role = role.name,
                opponentName = opponentName,
                openedAt = clock().toEpochMilli(),
                revealSeenAt = null,
                dismissed = false,
            ),
        )
        dao.merge(code.value, role.name, opponentName)
    }

    override suspend fun markRevealSeen(code: ShareCode, at: Instant) =
        dao.markRevealSeen(code.value, at.toEpochMilli())

    override suspend fun dismiss(code: ShareCode) = dao.dismiss(code.value)

    override fun opponentNames(): Flow<Map<String, String>> = dao.observeNamed()
        .map { rows -> rows.associate { it.code to requireNotNull(it.opponentName) } }
        .distinctUntilChanged()
}

private fun MatchLocalEntity.toLocalMatch(): LocalMatch? {
    val shareCode = ShareCode.parse(code) ?: return null
    return LocalMatch(
        code = shareCode,
        role = LocalRole.entries.firstOrNull { it.name == role } ?: LocalRole.Invite,
        opponentName = opponentName,
        openedAt = Instant.ofEpochMilli(openedAt),
        revealSeenAt = revealSeenAt?.let(Instant::ofEpochMilli),
        dismissed = dismissed,
    )
}
