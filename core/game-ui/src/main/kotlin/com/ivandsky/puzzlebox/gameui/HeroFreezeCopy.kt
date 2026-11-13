package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.StreakFreezes
import java.time.format.TextStyle
import java.util.Locale

/**
 * The streak card's sub line once freezes are on: the days left to earn the next freeze, or at
 * the cap (no days to count) the freezes held. Replaces [heroStreakSub], which only speaks of the streak.
 */
internal fun heroFreezeSub(freezes: StreakFreezes): String = when (val days = freezes.daysToNext) {
    null -> "${freezeCountLabel(freezes.held)} held"
    1 -> "1 more day to earn a freeze"
    else -> "$days more days to earn a freeze"
}

/**
 * The one line the Result shows when this solve spent or earned a freeze, with [held] the freezes
 * left after it. A solve that did both reads as the spend: the loss is the news, the earn rides
 * in the count. One spend names its weekday, several are counted.
 */
internal fun heroFreezeMoment(moment: StreakFreezes.Moment, held: Int, locale: Locale): String {
    val spent = moment.spent
    return when {
        spent.size == 1 ->
            "Streak freeze used for ${spent.single().dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} · $held left"
        spent.size > 1 -> "Used ${spent.size} freezes · $held left"
        else -> "Freeze earned · $held held"
    }
}
