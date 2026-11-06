package com.ivandsky.puzzlebox.game

/**
 * How a game ranks completions (ADR-0013). Both keys are "lower is better" (A3); the order
 * only decides which one leads. [TimeThenHints] is the shared default (Zip, Queens, Tango);
 * [HintsThenTime] ranks fewer hints first and breaks ties on time (Strands). [comparator]
 * is the one in-code ranking rule (smaller = better); only the records SQL mirrors it.
 */
enum class RankOrder(val comparator: Comparator<BestScore>) {
    TimeThenHints(compareBy<BestScore>({ it.timeMs }, { it.hints })),
    HintsThenTime(compareBy<BestScore>({ it.hints }, { it.timeMs })),
}
