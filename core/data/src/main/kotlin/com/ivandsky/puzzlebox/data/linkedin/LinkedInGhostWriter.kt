package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The only writer of the `ghosts` table and of the `saved_boards` rows a board read makes: turns a fetched
 * connections board into stored rows. Cap 50 connections; the player's own row is stored beside them (it is
 * told apart on read by its member ref) and never counts against the cap. A board is the player's own record
 * once read, so nothing prunes it by age. The caller passes both dates and the fetch time, so this class
 * reads no clock and nothing here leaves the phone.
 *
 * It takes no [LinkedInStoreLock] itself, which is not reentrant: the caller already holds it, as it does for
 * every LinkedIn store write.
 */
internal class LinkedInGhostWriter(
    private val db: LinkedInDatabase,
    private val ownerScope: OwnerScope,
) {

    private val ghosts = db.ghostDao()
    private val savedBoards = db.savedBoardDao()

    /**
     * Replaces the board of ([game], [day]) with [board] and writes its saved-board row in the same
     * transaction; other games and days are untouched. Returns the rows it kept, the player's own among
     * them. [today] is the game's cadence-zone date and [ownProfileId] the connected member. The saved
     * board is complete when it was read after the day's end, is stamped with the signed-in uid (null when
     * signed out) and has no acknowledged upload, even when it replaces one that had.
     */
    suspend fun write(
        game: GameId,
        day: LocalDate,
        today: LocalDate,
        ownProfileId: String,
        board: LinkedInBoard,
        fetchedAtMs: Long,
    ): List<LinkedInBoard.Row> {
        // The player's own result is their import, shown as the "you" row, so it is never a ghost: the row is
        // kept only for the player's LinkedIn name and photo. LinkedIn returns rows already ranked, so the cap
        // keeps the best 50 other rows.
        val own = board.rows.firstOrNull { it.memberRef == ownProfileId }
        val kept = board.rows
            .filter { it.memberRef != ownProfileId }
            .take(MAX_GHOSTS) + listOfNotNull(own)
        val rows = kept
            .map { row ->
                GhostEntity(
                    game = game.value,
                    day = day.toString(),
                    memberRef = row.memberRef,
                    name = row.name,
                    // A hidden time stays null: storing 0 would rank the member as the fastest.
                    timeMs = row.timeSeconds?.times(MS_PER_SECOND),
                    hintFree = row.hintFree,
                    flawless = row.flawless,
                    fetchedAtMs = fetchedAtMs,
                )
            }
        val saved = SavedBoardEntity(
            game = game.value,
            day = day.toString(),
            profileId = ownProfileId,
            ownerUid = ownerScope.writer(),
            readAtMs = fetchedAtMs,
            complete = today.isAfter(day),
            uploadedFingerprint = null,
        )
        db.withTransaction {
            ghosts.replace(game.value, day.toString(), rows)
            savedBoards.upsert(saved)
        }
        return kept
    }

    private companion object {
        const val MAX_GHOSTS = 50
        const val MS_PER_SECOND = 1000L
    }
}
