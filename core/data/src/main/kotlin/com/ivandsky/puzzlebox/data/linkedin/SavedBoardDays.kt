package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * A day's saved board as the phone shows it, read from `saved_boards` and the board's ghosts alone: no link, no
 * session and no age limit, so a disconnected player or a new device sees any day that was ever read. Only the
 * boards [OwnerScope.visible] may see exist here (ADR-0029 decision 6): the account's own and the ownerless ones.
 *
 * A (game, day) holds one saved board, so which profile's board a day shows is settled where boards are written
 * (the last read replaces, a restore keeps the linked profile's): this reader never chooses between two. The
 * player's own row is the one of the board's profile, whoever is linked now. Whether the day is still inside the
 * days LinkedIn serves is [LinkedInDayWindow]'s call, not this one's.
 */
internal class SavedBoardDays(
    private val db: LinkedInDatabase,
    private val avatars: LinkedInAvatars,
    private val ownerScope: OwnerScope,
) {

    private val savedBoards = db.savedBoardDao()
    private val ghosts = db.ghostDao()

    /**
     * The saved board of ([game], [day]), emitted again when the board, a photo or the visible owner changes.
     * [linkedProfileId] only matters for a day with no saved board: the linked player's name and photo still
     * stand in for their own row there.
     *
     * The board's rows are read together after each write to either table, never one table's flow beside the
     * other's, so a reader never sees the day's player without the connections of the board that named them.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(game: GameId, day: LocalDate, linkedProfileId: String?): Flow<Saved> =
        ownerScope.visible.flatMapLatest { visibleOwner ->
            val stored = db.invalidationTracker.createFlow("saved_boards", "ghosts")
                .map { read(game, day, linkedProfileId, visibleOwner) }
                .distinctUntilChanged()
            combine(stored, avatars.present) { rows, photos -> rows.toSaved(photos) }
        }.distinctUntilChanged()

    private suspend fun read(game: GameId, day: LocalDate, linkedProfileId: String?, visibleOwner: String?): Stored {
        val board = savedBoards.get(game.value, day.toString())?.takeIf { it.isVisibleTo(visibleOwner) }
        val profileId = board?.profileId ?: linkedProfileId
        val rows = if (board == null) emptyList() else ghosts.board(game.value, day.toString())
        // The player's name and photo belong to the account, so a day whose board holds no row of them (a past board
        // is never read again) shows the newest row of their profile, from any game and day.
        val own = rows.firstOrNull { it.memberRef == profileId }
            ?: profileId?.let { ghosts.newestOf(it, visibleOwner) }
        return Stored(board, profileId, rows, own)
    }

    private fun Stored.toSaved(photos: Set<String>) = Saved(
        readAtMs = board?.readAtMs,
        complete = board?.complete ?: false,
        ghosts = rows.filter { it.memberRef != profileId }.map { it.toGhost(avatars.pathOf(it.memberRef, photos)) },
        owner = own?.let { LinkedInOwner(it.name, avatars.pathOf(it.memberRef, photos)) },
    )

    private fun SavedBoardEntity.isVisibleTo(visibleOwner: String?): Boolean =
        visibleOwner == null || ownerUid == null || ownerUid == visibleOwner

    private fun GhostEntity.toGhost(photoPath: String?) = LinkedInGhost(memberRef, name, timeMs, hintFree, flawless, photoPath)

    /** What the tables hold for one day, before photos are matched to it. */
    private data class Stored(val board: SavedBoardEntity?, val profileId: String?, val rows: List<GhostEntity>, val own: GhostEntity?)

    /**
     * One day's stored board: [readAtMs] is null when none is saved for the visible owner, and then [ghosts]
     * is empty. [owner] is the player's own row, never one of the connections.
     */
    data class Saved(
        val readAtMs: Long?,
        val complete: Boolean,
        val ghosts: List<LinkedInGhost>,
        val owner: LinkedInOwner?,
    )
}
