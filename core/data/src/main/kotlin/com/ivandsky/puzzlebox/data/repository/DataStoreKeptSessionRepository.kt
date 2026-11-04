package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.datastore.KeptSessionsDataSource
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Kept sessions in their own `kept_sessions` DataStore file, through the core:datastore data source. */
internal class DataStoreKeptSessionRepository(
    private val dataSource: KeptSessionsDataSource,
) : KeptSessionRepository {

    override val kept: Flow<Map<GameId, KeptSession>> = dataSource.entries.map { entries ->
        entries.associate { entry ->
            entry.game to KeptSession(entry.game, entry.day, entry.elapsedMs, entry.envelope)
        }
    }

    override suspend fun keep(session: KeptSession) {
        dataSource.put(
            KeptSessionsDataSource.Entry(session.game, session.day, session.elapsedMs, session.envelope),
        )
    }

    override suspend fun discard(game: GameId) {
        dataSource.remove(game)
    }
}
