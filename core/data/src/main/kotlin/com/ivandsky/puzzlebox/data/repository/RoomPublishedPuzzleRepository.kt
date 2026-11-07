package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.database.PublishedPuzzleDao
import com.ivandsky.puzzlebox.database.PublishedPuzzleEntity
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Room adapter of [PublishedPuzzleRepository] over `published_puzzles` ([dao]). Codes are stored
 * in canonical form and instants as epoch ms; a row whose code no longer parses is skipped.
 */
internal class RoomPublishedPuzzleRepository(private val dao: PublishedPuzzleDao) : PublishedPuzzleRepository {

    // Room re-runs both queries on every `published_puzzles` write, including other rows'.
    override fun observeAll(): Flow<List<PublishedPuzzle>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toPublishedPuzzle() } }.distinctUntilChanged()

    override fun observe(code: ShareCode): Flow<PublishedPuzzle?> =
        dao.observe(code.value).map { it?.toPublishedPuzzle() }.distinctUntilChanged()

    override suspend fun upsert(puzzle: PublishedPuzzle) = dao.upsert(puzzle.toEntity())

    override suspend fun markUnpublished(code: ShareCode) = dao.markUnpublished(code.value)

    override suspend fun updateSolves(code: ShareCode, count: Int, at: Instant) =
        dao.updateSolves(code.value, count, at.toEpochMilli())
}

private fun PublishedPuzzle.toEntity() = PublishedPuzzleEntity(
    code = code.value,
    gameId = gameId.value,
    variant = variant.value,
    title = title,
    authorTimeMs = authorTimeMs,
    createdAt = createdAt.toEpochMilli(),
    unpublished = unpublished,
    solvesCount = solvesCount,
    solvesCountAt = solvesCountAt?.toEpochMilli(),
    authorHints = authorHints,
)

private fun PublishedPuzzleEntity.toPublishedPuzzle(): PublishedPuzzle? {
    val shareCode = ShareCode.parse(code) ?: return null
    return PublishedPuzzle(
        code = shareCode,
        gameId = GameId(gameId),
        variant = VariantId(variant),
        title = title,
        authorTimeMs = authorTimeMs,
        authorHints = authorHints,
        createdAt = Instant.ofEpochMilli(createdAt),
        unpublished = unpublished,
        solvesCount = solvesCount,
        solvesCountAt = solvesCountAt?.let(Instant::ofEpochMilli),
    )
}
