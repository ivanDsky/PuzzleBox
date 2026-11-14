package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.bestScoreText
import java.util.Locale

/**
 * [value] as a row's score text, in [order]'s voice where it leads with hints (Strands): "4:12", "Perfect · 4:12",
 * "avg 4:12", "0.7 hints · avg 3:05", "1,240 pts", and "≥1,240 pts" for a total that is only a lower bound.
 */
fun boardRowValueText(value: BoardRowValue, order: RankOrder): String = when (value) {
    is BoardRowValue.Timed -> bestScoreText(BestScore(value.timeMs, value.hints), order)
    is BoardRowValue.Average -> averageText(value, order)
    is BoardRowValue.Points -> pointsText(value)
}

/** The small chip beside a row's name that its [value] adds, if any: the number of games a points total counts. */
fun boardRowValueDetail(value: BoardRowValue): String? = when (value) {
    is BoardRowValue.Points -> if (value.games == 1) "1 game" else "${value.games} games"
    is BoardRowValue.Timed, is BoardRowValue.Average -> null
}

/** The time of [value] as a [ScoreTime], so a new time counts from the old one on the board's glide; points do not count. */
internal fun BoardRowValue.scoreTime(order: RankOrder): ScoreTime? = when (this) {
    is BoardRowValue.Timed -> ScoreTime(timeMs) { ms -> boardRowValueText(copy(timeMs = ms), order) }
    is BoardRowValue.Average -> ScoreTime(avgTimeMs) { ms -> boardRowValueText(copy(avgTimeMs = ms), order) }
    is BoardRowValue.Points -> null
}

private fun averageText(value: BoardRowValue.Average, order: RankOrder): String {
    val time = "avg ${formatDuration(value.avgTimeMs)}"
    return when (order) {
        RankOrder.TimeThenHints -> time
        RankOrder.HintsThenTime -> String.format(Locale.ROOT, "%.1f hints · %s", value.avgHints, time)
    }
}

private fun pointsText(value: BoardRowValue.Points): String =
    String.format(Locale.ROOT, "%s%,.0f pts", if (value.atLeast) "≥" else "", value.total)
