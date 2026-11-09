package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.SpreadBin
import java.util.Locale

/** One stat chip under the hero time: [tag] is its test tag, [label] sits under the [value]. */
internal data class HeroChip(val tag: String, val value: String, val label: String)

/** One dot of the week: [label] is the weekday's initial; [solved] fills it, [today] marks the last. */
internal data class HeroWeekDot(val label: String, val solved: Boolean, val today: Boolean)

/** The streak block: [days] counts up on screen; [title] and [sub] are its sentences. */
internal data class HeroStreak(val days: Int, val title: String, val sub: String, val week: List<HeroWeekDot>)

/**
 * The histogram block. [bins] is empty on a first solve, where [firstTimeLine] stands in and
 * the axis reads [axisLow]/[axisHigh] ("faster"/"slower"); otherwise [youBin] is the bin
 * holding this solve. [caption] is the sentence under the bars; [description] reads the chart
 * aloud.
 */
internal data class HeroSpread(
    val heading: String,
    val bins: List<SpreadBin>,
    val youBin: Int?,
    val axisLow: String,
    val axisHigh: String,
    val firstTimeLine: String?,
    val caption: Emphasized?,
    val description: String,
)

/** The rank row: a bold [value] and its [sub] line, or a sign-in prompt. */
internal sealed interface HeroRank {
    data class Place(val value: String, val sub: String) : HeroRank
    data class SignIn(val text: String) : HeroRank
}

/**
 * What the Results hero page shows, derived once from the facts, so the cards only draw.
 * A null [pill], [bestLine], [streak], [spread] or [rank] hides that element. [bestImproved]
 * paints the best line in the game's accent (a new best); [celebrate] is the entrance's
 * pulse; [burst] adds the burst (a new best only).
 */
internal data class ResultHeroModel(
    val title: String,
    val dateLine: String,
    val timeMs: Long,
    val pill: String?,
    val bestLine: String?,
    val bestImproved: Boolean,
    val chips: List<HeroChip>,
    val streak: HeroStreak?,
    val spread: HeroSpread?,
    val rank: HeroRank?,
    val celebrate: Boolean,
    val burst: Boolean,
    val primary: ResultPrimaryAction?,
)

/**
 * The model for [facts] (spec: Results hero). A practice puzzle (A22) counts nothing: no
 * pill, streak, spread or rank. A replay counts nothing either but shows the spread: no
 * pill, streak or rank. The streak, spread and rank wait for their data, so none shows a
 * placeholder; the Average chip hides on a first solve.
 * A non-null [chip] (a duel's count) replaces the Today chip with its own "In this duel" chip.
 */
internal fun resultHeroModel(
    facts: ResultHeroFacts,
    locale: Locale = Locale.getDefault(),
    chip: String? = null,
): ResultHeroModel {
    val counts = !facts.practice && !facts.replay
    val insights = facts.insights.takeUnless { facts.practice }
    val verdict = facts.verdict.takeUnless { facts.practice }
    val pill = heroPill(verdict).takeIf { counts }
    return ResultHeroModel(
        title = resultTitle(facts.game, facts.puzzleNumber, facts.practice),
        dateLine = facts.dateLine,
        timeMs = facts.timeMs,
        pill = pill,
        bestLine = if (facts.practice) {
            null
        } else {
            heroBestLine(
                verdict = if (facts.replay) RecordVerdict.Replay else verdict,
                previousBest = facts.previousBest,
                order = facts.rankOrder,
                timeMs = facts.timeMs,
                hintsUsed = facts.hintsUsed,
            )
        },
        bestImproved = counts && verdict == RecordVerdict.NewBest,
        chips = chips(facts, insights, chip),
        streak = insights?.takeIf { counts }?.let { streak(it, locale) },
        spread = insights?.let { spread(facts, it) },
        rank = if (counts) rank(facts.rank) else null,
        celebrate = counts && (verdict == RecordVerdict.NewBest || verdict == RecordVerdict.FirstSolve),
        burst = counts && verdict == RecordVerdict.NewBest,
        primary = facts.primary,
    )
}

private fun chips(facts: ResultHeroFacts, insights: ResultInsights?, duel: String?): List<HeroChip> = buildList {
    add(
        HeroChip(
            tag = "result.hero.chip.hints",
            value = if (facts.hintsUsed == 0) "None" else "${facts.hintsUsed}",
            label = if (facts.hintsUsed == 1) "Hint" else "Hints",
        ),
    )
    if (duel != null) {
        add(HeroChip("result.hero.chip.duel", duel, "In this duel"))
    } else if (facts.doneToday != null && facts.total != null && facts.total > 0) {
        add(HeroChip("result.hero.chip.today", "${facts.doneToday} of ${facts.total}", "Today"))
    }
    insights?.averageMs?.let { add(HeroChip("result.hero.chip.average", formatDuration(it), "Average")) }
}

private fun streak(insights: ResultInsights, locale: Locale) = HeroStreak(
    days = insights.streak,
    title = heroStreakTitle(insights.streak),
    sub = heroStreakSub(insights.streak),
    week = insights.week.map { HeroWeekDot(weekdayInitial(it.date, locale), it.solved, it.today) },
)

private fun spread(facts: ResultHeroFacts, insights: ResultInsights): HeroSpread {
    val spread = insights.spread
    val heading = heroSpreadHeading(facts.game)
    if (spread.isFirstSolve) {
        return HeroSpread(
            heading = heading,
            bins = emptyList(),
            youBin = null,
            axisLow = "faster",
            axisHigh = "slower",
            firstTimeLine = HERO_FIRST_TIME_LINE,
            caption = null,
            description = HERO_FIRST_TIME_LINE,
        )
    }
    val caption = heroSpreadCaption(facts.rankOrder, spread.best, spread.beaten, spread.solves, insights.averageMs)
    return HeroSpread(
        heading = heading,
        bins = spread.bins,
        youBin = spread.youBin,
        axisLow = formatDuration(spread.loMs),
        axisHigh = formatDuration(spread.hiMs),
        firstTimeLine = null,
        caption = caption,
        description = "$heading. ${caption.text}",
    )
}

private fun rank(rank: ResultRank): HeroRank? = when (rank) {
    ResultRank.Hidden -> null
    ResultRank.SignedOut -> HeroRank.SignIn(HERO_SIGN_IN_LINE)
    is ResultRank.Ranked -> HeroRank.Place(
        value = heroRankValue(rank.position, rank.count, rank.topPercent),
        sub = heroRankSub(rank.count),
    )
}
