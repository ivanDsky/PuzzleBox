package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.KeptSession
import com.ivandsky.puzzlebox.data.repository.KeptSessionRepository
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [KeptSessionRepository] with the real one's semantics: one entry per game,
 * [keep] replaces, [discard] removes, every change re-emits the whole map. Like the real
 * store it applies no day policy. [current] reads the map synchronously for assertions.
 */
class FakeKeptSessionRepository(sessions: List<KeptSession> = emptyList()) : KeptSessionRepository {

    private val all = MutableStateFlow(sessions.associateBy { it.game })

    /** What is kept right now. */
    val current: Map<GameId, KeptSession> get() = all.value

    override val kept: Flow<Map<GameId, KeptSession>> = all

    override suspend fun keep(session: KeptSession) {
        all.update { it + (session.game to session) }
    }

    override suspend fun discard(game: GameId) {
        all.update { it - game }
    }
}
