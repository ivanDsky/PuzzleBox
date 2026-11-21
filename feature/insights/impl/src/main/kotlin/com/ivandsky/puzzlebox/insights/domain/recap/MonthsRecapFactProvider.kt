package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth

/**
 * The month cards of a yearly recap: one for each month with a counted solve, January first, each with its count, its
 * quickest solve, its best against the player's average (A3), the Moments it earned, and its headline. Every month lists
 * what happened in it as [MonthHighlight] candidates, and [SpotlightMonths] picks the headlines once over the whole year,
 * so a game that led one month gives way in the next. They sit right after the totals cards (Total time is the last of
 * them), before the year finale. A month's own recap is the month already, so it gets none.
 */
internal object MonthsRecapFactProvider : RecapFactProvider {

    /** A game's best of the month counts as running under its average from this many percent under it. */
    private const val MIN_UNDER_AVERAGE_PERCENT = 10

    /** A game's busiest month counts from this percent of its usual month, in a year of at least [MIN_MONTHS_FOR_BUSIEST] months. */
    private const val MIN_BUSIEST_PERCENT = 112
    private const val MIN_MONTHS_FOR_BUSIEST = 4

    /** A month is quiet under one part in this many of the year's usual month. */
    private const val QUIET_PARTS = 4

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        if (input.facts.period !is RecapPeriod.Year) return emptyList()
        val months = input.facts.months
        val moments = input.facts.moments.mapNotNull { it.earned() }.groupBy { YearMonth.from(it.day) }
        val year = Year(input)
        val picks = SpotlightMonths.pick(months.map { year.candidates(it, moments[it.month].orEmpty()) })
        return months.zip(picks) { month, pick ->
            RecapCard.Month(
                month = month.month,
                solved = month.solved,
                bestGame = month.bestGame,
                bestTimeMs = month.bestTimeMs,
                underAveragePercent = month.underAveragePercent,
                fastestGame = month.fastestGame,
                fastestTimeMs = month.fastestTimeMs,
                lead = pick.lead,
                also = pick.also,
                moments = moments[month.month].orEmpty(),
            )
        }
    }

    /**
     * What a month's candidates are measured against: each game's month counts over the year (its usual month is their
     * lower middle, its peak the largest), the year's usual month (the lower middle of the months' counts), each game's
     * quickest solve of the year, and the year's days.
     */
    private class Year(private val input: RecapInput) {
        private val facts = input.facts
        private val monthCounts: Map<GameId, List<Int>> = facts.months
            .flatMap { it.byGame.entries }
            .groupBy({ it.key }, { it.value.monthSolves })
            .mapValues { (_, counts) -> counts.sorted() }
        private val usualMonth = facts.months.map { it.solved }.sorted().lowerMiddle()
        private val daysByMonth = facts.dayTallies.groupBy { YearMonth.from(it.day) }

        /**
         * [month]'s candidates in candidate order: its Moments ([earned]) by day, its full sets, then per game in the app's
         * order its run under the average and its busiest month, then its busiest day and a quiet month. A month with
         * nothing but its busiest day adds what most of it was.
         */
        fun candidates(month: MonthFacts, earned: List<EarnedMoment>): List<MonthHighlight> {
            val games = input.inGameOrder(month.byGame)
            val found = buildList {
                addAll(moments(month, earned))
                fullSets(earned)?.let(::add)
                games.forEach { (game, best) ->
                    underAverage(game, best)?.let(::add)
                    busiestMonth(game, best)?.let(::add)
                }
                busiestDay(month)?.let(::add)
                quiet(month, games)?.let(::add)
            }
            return if (found.all { it is MonthHighlight.BusiestDay }) found + focus(month, games) else found
        }

        /**
         * The month's personal bests, firsts under a time and streaks, by day and in that order within a day. A game's
         * personal bests come down to one: the one on its quickest solve of the month, whose time that is, or else its latest.
         */
        private fun moments(month: MonthFacts, earned: List<EarnedMoment>): List<MonthHighlight> {
            val bests = earned.filter { it.kind == EarnedMoment.Kind.PersonalBest }
                .groupBy { checkNotNull(it.game) }
                .map { (game, ofGame) -> ofGame.firstOrNull { it.day == month.byGame[game]?.day } ?: ofGame.last() }
            return earned.filter { it.kind != EarnedMoment.Kind.PersonalBest || it in bests }
                .sortedWith(compareBy({ it.day }, { it.kind }))
                .mapNotNull { highlight(month, it) }
        }

        private fun highlight(month: MonthFacts, moment: EarnedMoment): MonthHighlight? = when (moment.kind) {
            EarnedMoment.Kind.PersonalBest -> {
                val game = checkNotNull(moment.game)
                val best = month.byGame[game]
                MonthHighlight.PersonalBest(
                    game = game,
                    day = moment.day,
                    timeMs = best?.timeMs?.takeIf { best.day == moment.day },
                    fastestAllYear = facts.fastestByGame[game]?.day == moment.day,
                )
            }
            EarnedMoment.Kind.FirstSubX -> MonthHighlight.FirstSubX(checkNotNull(moment.game), moment.day, checkNotNull(moment.thresholdMs))
            EarnedMoment.Kind.Streak -> MonthHighlight.Streak(moment.day, checkNotNull(moment.streakDays))
            // The month's full sets are one candidate, after its other Moments.
            EarnedMoment.Kind.FullSet -> null
        }

        private fun fullSets(earned: List<EarnedMoment>): MonthHighlight.FullSets? =
            earned.filter { it.kind == EarnedMoment.Kind.FullSet }
                .takeIf { it.isNotEmpty() }
                ?.let { sets -> MonthHighlight.FullSets(day = sets.last().day, count = sets.size) }

        /** [game]'s best of the month against its year average, when it has one to beat (more than one solve in the year). */
        private fun underAverage(game: GameId, best: MonthFacts.MonthBest): MonthHighlight.UnderAverage? =
            best.underAveragePercent
                .takeIf { best.yearSolves > 1 && best.timeMs > 0 && it >= MIN_UNDER_AVERAGE_PERCENT }
                ?.let { MonthHighlight.UnderAverage(game, it, best.timeMs, best.averageMs) }

        /** [game]'s peak month, well over its usual month. */
        private fun busiestMonth(game: GameId, best: MonthFacts.MonthBest): MonthHighlight.BusiestMonth? {
            val counts = monthCounts.getValue(game)
            val usual = counts.lowerMiddle()
            val solved = best.monthSolves
            val busiest = facts.months.size >= MIN_MONTHS_FOR_BUSIEST && solved == counts.last() && solved * 100 >= usual * MIN_BUSIEST_PERCENT
            return if (busiest) MonthHighlight.BusiestMonth(game, solved, usual) else null
        }

        /** The month's day with the most solves; of equal days, the earlier (a day's tally does not say which games it had). */
        private fun busiestDay(month: MonthFacts): MonthHighlight.BusiestDay? =
            daysByMonth[month.month]
                ?.maxWithOrNull(compareBy<DayTally> { it.solved }.thenByDescending { it.day })
                ?.let { MonthHighlight.BusiestDay(it.day, it.solved) }

        private fun quiet(month: MonthFacts, games: List<Pair<GameId, MonthFacts.MonthBest>>): MonthHighlight.QuietMonth? =
            if (month.solved * QUIET_PARTS < usualMonth) MonthHighlight.QuietMonth(month.solved, mostPlayed(games).first) else null

        private fun focus(month: MonthFacts, games: List<Pair<GameId, MonthFacts.MonthBest>>): MonthHighlight.Focus {
            val (game, best) = mostPlayed(games)
            return MonthHighlight.Focus(game, best.monthSolves, month.solved)
        }

        /** The game with the most solves of the month; of equal games, the one the app lists first. */
        private fun mostPlayed(games: List<Pair<GameId, MonthFacts.MonthBest>>) = games.maxBy { it.second.monthSolves }

        private fun List<Int>.lowerMiddle(): Int = if (isEmpty()) 0 else this[(size - 1) / 2]
    }
}
