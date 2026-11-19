package com.ivandsky.puzzlebox.data.rankhistory

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * Where the player's kept weekly Friends rank lives, on this device only: it never syncs, and a new device refills
 * it from the cloud for the last weeks only. A week is kept once and never rewritten, so a friend who joins later
 * does not change a past week. Only the owner's own numbers are kept, never a friend's name or id.
 */
interface RankHistoryStore {

    /** The kept weeks of [ownerUid] in [game] whose [Week.weekStart] is in [weekStarts], oldest first. */
    suspend fun weeks(ownerUid: String, game: GameId, weekStarts: ClosedRange<LocalDate>): List<Week>

    /**
     * Keeps [week] unless its owner, game and week start is already kept; a kept week is left exactly as it was.
     * Returns true when this call kept it, false when an earlier week stood.
     */
    suspend fun insertOnce(week: Week): Boolean

    /**
     * One kept week of one owner in one game. [pointsPerDay] is the owner's own Friends points for the week averaged
     * per played day and [friendCount] is how many friends stood on the boards then; [formatVersion] says which
     * reading of those two numbers the row was kept under.
     */
    data class Week(
        val ownerUid: String,
        val game: GameId,
        val weekStart: LocalDate,
        val pointsPerDay: Double,
        val friendCount: Int,
        val formatVersion: Int = FORMAT_VERSION,
    )

    companion object {
        /** The row shape this build keeps. */
        const val FORMAT_VERSION = 1
    }
}
