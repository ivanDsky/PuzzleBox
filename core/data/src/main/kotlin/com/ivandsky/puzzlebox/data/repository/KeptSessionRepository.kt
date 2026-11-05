package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow

/**
 * Kept sessions survive app restarts (spec A3): at most one per game, keyed by game id and
 * game-agnostic over the SessionBlob envelope. The repository applies no day policy; the play
 * store owns it (A4).
 */
interface KeptSessionRepository {

    /** Every kept session by game, re-emitted on each change. */
    val kept: Flow<Map<GameId, KeptSession>>

    /** Keeps [session], replacing whatever its game held. */
    suspend fun keep(session: KeptSession)

    /** Drops [game]'s kept session; a game with none is a no-op. */
    suspend fun discard(game: GameId)
}
