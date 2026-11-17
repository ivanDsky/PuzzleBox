package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.first

/**
 * The solved puzzles a self duel can draw from: the solved dailies (with a source number) of the registered
 * [games], plus the solved friend puzzles this device still holds with [includeFriends]. Each puzzle is listed
 * once; [CreateSelfDuel] draws from exactly this list.
 */
class ComputeSolvedPool(
    private val registry: GameRegistry,
    private val completions: CompletionRepository,
    private val received: ReceivedPuzzleRepository,
) {
    suspend operator fun invoke(games: Set<GameId>, includeFriends: Boolean): List<PuzzleRef> =
        completions.observeRecords().first()
            .map { it.puzzleRef }
            .filter { it.gameId in games && registry[it.gameId] != null && it.drawable(includeFriends) }
            .distinctBy { it.gameId to it.key }

    private suspend fun PuzzleRef.drawable(includeFriends: Boolean): Boolean = when (val k = key) {
        is PuzzleKey.Daily -> k.puzzleNumber != null
        is PuzzleKey.Custom -> includeFriends && ShareCode.parse(k.puzzleId)?.let { received.get(it) }?.unpublished == false
    }
}
