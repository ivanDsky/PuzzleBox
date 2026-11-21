package com.ivandsky.puzzlebox.resultui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.resultui.bestScoreText
import java.util.Locale

/**
 * [value] as a row's score text, in [order]'s voice where it leads with hints (Strands): "4:12", "Perfect · 4:12",
 * "1,240 pts", and "≥1,240 pts" for a total that is only a lower bound.
 */
fun boardRowValueText(value: BoardRowValue, order: RankOrder): String = when (value) {
    is BoardRowValue.Timed -> bestScoreText(BestScore(value.timeMs, value.hints), order)
    is BoardRowValue.Points -> pointsText(value.total, value.atLeast)
    is BoardRowValue.DayPoints -> pointsText(value.total, value.atLeast)
}

/** The small line under a row's name that its [value] adds, if any: the games or days a points total counts. */
fun boardRowValueDetail(value: BoardRowValue): String? = when (value) {
    is BoardRowValue.Points -> if (value.games == 1) "1 game" else "${value.games} games"
    is BoardRowValue.DayPoints -> if (value.days == 1) "1 day" else "${value.days} days"
    is BoardRowValue.Timed -> null
}

/** The time of [value] as a [ScoreTime], so a new time counts from the old one on the board's glide; points do not count. */
internal fun BoardRowValue.scoreTime(order: RankOrder): ScoreTime? = when (this) {
    is BoardRowValue.Timed -> ScoreTime(timeMs) { ms -> boardRowValueText(copy(timeMs = ms), order) }
    is BoardRowValue.Points, is BoardRowValue.DayPoints -> null
}

private fun pointsText(total: Double, atLeast: Boolean): String =
    String.format(Locale.ROOT, "%s%,.0f pts", if (atLeast) "≥" else "", total)
