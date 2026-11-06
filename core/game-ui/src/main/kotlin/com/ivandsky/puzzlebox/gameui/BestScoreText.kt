package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * [best] as one short string, led by the key [order] ranks first: time-first reads "4:12";
 * hints-first reads "Perfect · 4:12" for a hint-free best, else "2 hints · 3:05".
 */
fun bestScoreText(best: BestScore, order: RankOrder): String = when (order) {
    RankOrder.TimeThenHints -> formatDuration(best.timeMs)
    RankOrder.HintsThenTime -> "${hintsLabel(best.hints)} · ${formatDuration(best.timeMs)}"
}

private fun hintsLabel(hints: Int): String = when (hints) {
    0 -> "Perfect"
    1 -> "1 hint"
    else -> "$hints hints"
}
