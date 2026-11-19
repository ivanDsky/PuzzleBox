package com.ivandsky.puzzlebox.play.domain.moments

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.ranks.RankedBoard

/**
 * What [detectMoments] knows about one solve. Everything but [friends] is on the phone when the solve lands.
 *
 * @property playAgain the solve replays a day that is not today; it counts nothing.
 * @property practice the puzzle has no edition number; it counts nothing.
 * @property newBest the record verdict called this solve the game's new best.
 * @property bestHintFreeMs the game's best hint-free time before this solve; null when it had none.
 * @property doneToday today's solved games, this solve included.
 * @property totalToday the games in today's set.
 * @property dayTotal the time and hints summed over today's set, this solve included; null when the total is not asked for.
 * @property streakBefore the streak before this solve.
 * @property streak the streak with this solve counted.
 * @property friends today's Friends board for this game; null until it is read, and when it cannot be.
 */
internal data class SolveFacts(
    val timeMs: Long,
    val hints: Int,
    val playAgain: Boolean,
    val practice: Boolean,
    val newBest: Boolean,
    val bestHintFreeMs: Long?,
    val doneToday: Int,
    val totalToday: Int,
    val dayTotal: Moment.FullSetDone.DayTotal? = null,
    val streakBefore: Int,
    val streak: Int,
    val friends: FriendDay? = null,
) {
    /**
     * Today's Friends board for the solved game, ranked among the viewer and their friends only,
     * as it stood [before] this solve and as it stands [after] it.
     */
    data class FriendDay(val viewerUid: String, val before: RankedBoard, val after: RankedBoard)
}
