package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine

/**
 * What the tablet play side panel shows beside the board (A15), besides the live clock
 * the route reads off the controller: today's set progress ([solvedToday] of [gamesToday],
 * null until the set loads), this game's [streak] (null until its stats load) with its
 * [week], [best] and [averageMs], and today's [times] on this game's board. [freezesHeld] is the streak
 * freezes the player holds (null with freezes off, which keeps the streak line and strip as they were). [linkedIn] says the LinkedIn account is
 * on, which is what lets the times card's rows move, arrive and leave with motion.
 */
internal data class PlayStatsState(
    val gameName: String,
    val solvedToday: Int?,
    val gamesToday: Int,
    val streak: Int?,
    val week: List<WeekDay>,
    val best: BestScore?,
    val rankOrder: RankOrder,
    val averageMs: Long?,
    val times: PlayTimes,
    val linkedIn: Boolean = false,
    val freezesHeld: Int? = null,
)

/** This game's board for the slot's day, as the panel can show it. */
internal sealed interface PlayTimes {
    data object Loading : PlayTimes

    /** The board is identity-gated (A7/A15), so a signed-out player sees no rows. */
    data object SignedOut : PlayTimes

    /** The backend can't serve the board right now (A7): a soft state, never an error. */
    data object Unavailable : PlayTimes

    /**
     * Every posted time in board order, as the leaderboard's own lines (a connection with a hidden time has
     * no line here); [rows] is empty before anyone has posted. [solved]
     * is the player's own solve of the slot, whose time can trail it onto the board.
     * [order] is the game's rank rule when the per-game board is on (rows then lead with
     * its key and the label names the rule); null keeps the time-only rows. [post] is how
     * the player's own post stands while its row is missing, read only with the per-game
     * board on; null keeps the plain Posting line. [pastDay] says the slot's day is
     * outside the submit window (A7), so its solve can never post — the card then says
     * so instead of any post status (RG10).
     */
    data class Board(
        val rows: List<DailyBoardLine>,
        val solved: Boolean,
        val order: RankOrder? = null,
        val post: SubmitStatus? = null,
        val pastDay: Boolean = false,
    ) : PlayTimes {

        private val you: Int = rows.indexOfFirst { it is DailyBoardLine.Real && it.row.isSelf }

        /** Whether the player's own row is on the board. */
        val hasOwnRow: Boolean
            get() = you >= 0

        /** The first [TOP] rows; never the player's own row appended below them. */
        val head: List<DailyBoardLine>
            get() = rows.take(TOP)

        /** The player's own row when it ranks below the [head] rows; null when it is inside them or absent. */
        val pinned: DailyBoardLine.Real?
            get() = if (you < TOP) null else rows[you] as DailyBoardLine.Real

        companion object {
            /** How many rows lead the card before the player's own is pinned under them. */
            const val TOP = 5
        }
    }
}
