package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.WeekDay

/**
 * What the tablet play side panel shows beside the board (A15), besides the live clock
 * the route reads off the controller: today's set progress ([solvedToday] of [gamesToday],
 * null until the set loads), this game's [streak] (null until its stats load) with its
 * [week], [best] and [averageMs], and today's [times] on this game's board.
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
)

/** This game's board for the slot's day, as the panel can show it. */
internal sealed interface PlayTimes {
    data object Loading : PlayTimes

    /** The board is identity-gated (A7/A15), so a signed-out player sees no rows. */
    data object SignedOut : PlayTimes

    /** The backend can't serve the board right now (A7): a soft state, never an error. */
    data object Unavailable : PlayTimes

    /**
     * Every posted time in board order; [rows] is empty before anyone has posted. [solved]
     * is the player's own solve of the slot, whose time can trail it onto the board.
     */
    data class Board(val rows: List<PlayTimeRow>, val solved: Boolean) : PlayTimes {

        private val you: Int = rows.indexOfFirst { it.you }

        /** The first [TOP] rows, with the player's own row pinned below when it ranks lower. */
        val top: List<PlayTimeRow>
            get() = if (you < TOP) rows.take(TOP) else rows.take(TOP) + rows[you]

        /** The player's row with [NEAR] ranks either side; null until the player has posted. */
        val nearYou: List<PlayTimeRow>?
            get() = if (you < 0) null else rows.subList((you - NEAR).coerceAtLeast(0), (you + NEAR + 1).coerceAtMost(rows.size))

        private companion object {
            const val TOP = 5
            const val NEAR = 2
        }
    }
}

/** One posted time: its board [rank] (1-based), who, and whether it is the player's. */
internal data class PlayTimeRow(val rank: Int, val name: String, val timeMs: Long, val you: Boolean)
