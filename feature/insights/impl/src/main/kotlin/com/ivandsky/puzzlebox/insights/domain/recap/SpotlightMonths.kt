package com.ivandsky.puzzlebox.insights.domain.recap

/**
 * The Spotlight rule (prototype 14, variant A): which of a month's [MonthHighlight]s its card leads with, and which it
 * adds as "also", so a year's headlines spread across games instead of one game winning best against average every
 * month. It runs once over the whole year, because a lead costs the months after it: each candidate scores its base,
 * less [REPEAT_PENALTY] for every earlier month a lead had its key, plus [FRESH_BONUS] when none did, less
 * [SAME_AS_PREVIOUS_PENALTY] when it has the previous month's lead key. A key is the game for a highlight about one
 * game, and the kind for the rest (a streak, full sets, the busiest day, a quiet month).
 */
internal object SpotlightMonths {

    /** A month's headline: its [lead], and an [also] with another key that is not the busiest day; null when there is none. */
    data class Pick(val lead: MonthHighlight, val also: MonthHighlight?)

    private const val REPEAT_PENALTY = 16
    private const val FRESH_BONUS = 8
    private const val SAME_AS_PREVIOUS_PENALTY = 10

    /**
     * One [Pick] per month of the year's [months], January first. Each month lists its candidates in candidate order
     * (its Moments by day, then its full sets, then each game's under-average and busiest month in the app's game order,
     * then its busiest day, a quiet month, and what most of it was), and needs at least one. The lead scores highest,
     * then has the higher base, then comes first in candidate order; the also is the next in that ranking that may be one.
     */
    fun pick(months: List<List<MonthHighlight>>): List<Pick> {
        val leads = mutableMapOf<Any, Int>()
        var previous: Any? = null
        return months.map { candidates ->
            val ranked = candidates.sortedWith(
                compareByDescending<MonthHighlight> { score(it, leads[it.key] ?: 0, it.key == previous) }.thenByDescending { base(it) },
            )
            val lead = ranked.first()
            leads[lead.key] = (leads[lead.key] ?: 0) + 1
            previous = lead.key
            Pick(lead, ranked.firstOrNull { it.key != lead.key && it !is MonthHighlight.BusiestDay })
        }
    }

    private fun score(highlight: MonthHighlight, earlierLeads: Int, sameAsPrevious: Boolean): Int =
        base(highlight) - REPEAT_PENALTY * earlierLeads +
            (if (earlierLeads == 0) FRESH_BONUS else 0) -
            (if (sameAsPrevious) SAME_AS_PREVIOUS_PENALTY else 0)

    /** The game for a highlight about one game; the kind for the rest. */
    private val MonthHighlight.key: Any get() = game ?: this::class

    /**
     * What a candidate is worth before the rotation, per the port spec's table. Halves round up, as the prototype's
     * Math.round does on these positive values.
     */
    private fun base(highlight: MonthHighlight): Int = when (highlight) {
        is MonthHighlight.PersonalBest -> 80 + if (highlight.fastestAllYear) 10 else 0
        is MonthHighlight.FirstSubX -> 74
        is MonthHighlight.Streak -> 66 + if (highlight.days >= 100) 10 else 0
        is MonthHighlight.FullSets -> minOf(52, 40 + 4 * (highlight.count - 1))
        // round(0.6 × percent), at most 44.
        is MonthHighlight.UnderAverage -> minOf(44, (6 * highlight.percent + 5) / 10)
        // round(22 + 60 × (solved ÷ usual − 1)).
        is MonthHighlight.BusiestMonth -> with(highlight) { (2 * (22 * usualSolved + 60 * (solved - usualSolved)) + usualSolved) / (2 * usualSolved) }
        is MonthHighlight.BusiestDay -> if (highlight.solved >= 3) 36 else 14
        is MonthHighlight.QuietMonth -> 90
        // 12 + round(8 × solved ÷ of).
        is MonthHighlight.Focus -> 12 + (16 * highlight.solved + highlight.of) / (2 * highlight.of)
    }
}
