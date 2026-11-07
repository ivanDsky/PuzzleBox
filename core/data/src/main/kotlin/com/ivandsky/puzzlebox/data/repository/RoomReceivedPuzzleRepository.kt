package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.database.ReceivedPuzzleDao
import com.ivandsky.puzzlebox.database.ReceivedPuzzleEntity
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Room adapter of [ReceivedPuzzleRepository] over `received_puzzles` ([dao]). Codes are stored in
 * canonical form, instants as epoch ms, [PostState] and [Difficulty.Level] by name; an unknown
 * name reads as [PostState.Pending] or no difficulty. A row whose code no longer parses is skipped.
 */
internal class RoomReceivedPuzzleRepository(private val dao: ReceivedPuzzleDao) : ReceivedPuzzleRepository {

    // Room re-runs both queries on every `received_puzzles` write, including other rows'.
    override fun observeAll(): Flow<List<ReceivedPuzzle>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toReceivedPuzzle() } }.distinctUntilChanged()

    override fun observe(code: ShareCode): Flow<ReceivedPuzzle?> =
        dao.observe(code.value).map { it?.toReceivedPuzzle() }.distinctUntilChanged()

    override suspend fun get(code: ShareCode): ReceivedPuzzle? = dao.get(code.value)?.toReceivedPuzzle()

    override suspend fun upsert(puzzle: ReceivedPuzzle) = dao.upsert(puzzle.toEntity())

    override suspend fun delete(code: ShareCode) = dao.delete(code.value)

    override suspend fun updatePost(code: ShareCode, state: PostState, attempts: Int) =
        dao.updatePost(code.value, state.name, attempts)

    override suspend fun markUnpublished(code: ShareCode) = dao.markUnpublished(code.value)
}

private fun ReceivedPuzzle.toEntity() = ReceivedPuzzleEntity(
    code = code.value,
    gameId = gameId.value,
    variant = variant.value,
    sharedFormatVersion = sharedFormatVersion,
    puzzleBytes = puzzleBytes,
    title = title,
    authorName = authorName,
    note = note,
    authorTimeMs = authorTimeMs,
    authorHints = authorHints,
    authorUid = authorUid,
    openedAt = openedAt.toEpochMilli(),
    unpublished = unpublished,
    postState = postState.name,
    postAttempts = postAttempts,
    difficulty = difficulty?.name,
)

private fun ReceivedPuzzleEntity.toReceivedPuzzle(): ReceivedPuzzle? {
    val shareCode = ShareCode.parse(code) ?: return null
    return ReceivedPuzzle(
        code = shareCode,
        gameId = GameId(gameId),
        variant = VariantId(variant),
        sharedFormatVersion = sharedFormatVersion,
        puzzleBytes = puzzleBytes,
        title = title,
        authorName = authorName,
        note = note,
        authorTimeMs = authorTimeMs,
        authorHints = authorHints,
        authorUid = authorUid,
        openedAt = Instant.ofEpochMilli(openedAt),
        unpublished = unpublished,
        // A name this version does not know was written by a newer app before a downgrade.
        postState = PostState.entries.firstOrNull { it.name == postState } ?: PostState.Pending,
        postAttempts = postAttempts,
        difficulty = Difficulty.Level.entries.firstOrNull { it.name == difficulty },
    )
}
