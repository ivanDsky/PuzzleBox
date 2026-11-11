package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.database.linkedin.GhostDao
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The only writer of the `ghosts` table: turns a fetched connections board into stored rows. Cap 50,
 * the player's own row dropped, days older than `today - 8` pruned on every write. The caller passes
 * both dates and the fetch time, so this class reads no clock and nothing here leaves the phone.
 */
internal class LinkedInGhostWriter(private val dao: GhostDao) {

    /**
     * Replaces the board of ([game], [day]) with [board]; other games and days are untouched. Returns the
     * rows it kept. [today] is the game's cadence-zone date and [ownProfileId] the connected member.
     */
    suspend fun write(
        game: GameId,
        day: LocalDate,
        today: LocalDate,
        ownProfileId: String,
        board: LinkedInBoard,
        fetchedAtMs: Long,
    ): List<LinkedInBoard.Row> {
        // The player's own result is their import, shown as the "you" row, so it is never a ghost.
        // LinkedIn returns rows already ranked, so the cap keeps the best 50 non-own rows.
        val kept = board.rows
            .filter { it.memberRef != ownProfileId }
            .take(MAX_GHOSTS)
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
        dao.replace(game.value, day.toString(), rows)
        // Days are ISO strings, so the DAO's string comparison is a date comparison. Pruning on every
        // write, even an empty one, keeps old boards from outliving the eight-day window.
        dao.pruneBefore(today.minusDays(KEEP_DAYS).toString())
        return kept
    }

    private companion object {
        const val MAX_GHOSTS = 50
        const val KEEP_DAYS = 8L
        const val MS_PER_SECOND = 1000L
    }
}
