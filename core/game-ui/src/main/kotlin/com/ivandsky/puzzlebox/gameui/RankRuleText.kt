package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.RankOrder

/** The rule a board ranks by (A15): "Fastest first, then fewer hints" / "Fewest hints first, then time". */
fun rankRuleText(order: RankOrder): String = when (order) {
    RankOrder.TimeThenHints -> "Fastest first, then fewer hints"
    RankOrder.HintsThenTime -> "Fewest hints first, then time"
}
