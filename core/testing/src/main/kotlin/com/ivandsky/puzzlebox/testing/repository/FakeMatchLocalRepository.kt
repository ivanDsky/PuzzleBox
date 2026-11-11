package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [MatchLocalRepository]; [clock] stamps `openedAt` of new rows. It passes the same
 * contract test as the Room adapter, so it cannot drift.
 */
class FakeMatchLocalRepository(private val clock: () -> Instant = Instant::now) : MatchLocalRepository {

    private val store = MutableStateFlow<Map<ShareCode, LocalMatch>>(emptyMap())

    override fun observeAll(): Flow<List<LocalMatch>> =
        store.map { rows -> rows.values.sortedByDescending { it.openedAt } }.distinctUntilChanged()

    override suspend fun remember(code: ShareCode, role: LocalRole, opponentName: String?) {
        store.update { rows ->
            val old = rows[code]
            val next = if (old == null) {
                LocalMatch(code, role, opponentName, clock().truncatedTo(ChronoUnit.MILLIS), null, false)
            } else {
                old.copy(
                    role = if (old.role == LocalRole.Participant) old.role else role,
                    opponentName = opponentName ?: old.opponentName,
                )
            }
            rows + (code to next)
        }
    }

    override suspend fun markRevealSeen(code: ShareCode, at: Instant, myScore: Int, theirScore: Int) {
        val stamp = at.truncatedTo(ChronoUnit.MILLIS)
        store.update { rows ->
            val old = rows[code] ?: LocalMatch(code, LocalRole.Participant, null, stamp, null, false)
            val next = if (old.revealSeenAt == null) {
                old.copy(revealSeenAt = stamp, myScore = myScore, theirScore = theirScore)
            } else {
                old
            }
            rows + (code to next)
        }
    }

    override suspend fun dismiss(code: ShareCode) {
        edit(code) { it.copy(dismissed = true) }
    }

    override fun opponentNames(): Flow<Map<String, String>> = store
        .map { rows -> rows.values.mapNotNull { m -> m.opponentName?.let { m.code.value to it } }.toMap() }
        .distinctUntilChanged()

    private fun edit(code: ShareCode, change: (LocalMatch) -> LocalMatch) {
        store.update { rows -> rows[code]?.let { rows + (code to change(it)) } ?: rows }
    }
}
