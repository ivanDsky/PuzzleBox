package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.ResultInsights
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.SpreadScope
import com.ivandsky.puzzlebox.domain.heldWhileStreak
import java.util.Locale

/**
 * One dot of the week: [label] is the weekday's initial; [solved] fills it, [today] marks the last.
 * A [frozen] day was held by a streak freeze and is never [solved].
 */
internal data class HeroWeekDot(val label: String, val solved: Boolean, val today: Boolean, val frozen: Boolean = false)

/**
 * The streak block: [days] counts up on screen; [title] and [sub] are its sentences. [freezesHeld] is the
 * freeze count beside the number, already ruled by [heldWhileStreak]; null hides it. [moment] is the line for a
 * freeze this solve spent or earned; null leaves it out (a play again never has one: it spends and earns nothing).
 */
internal data class HeroStreak(
    val days: Int,
    val title: String,
    val sub: String,
    val week: List<HeroWeekDot>,
    val freezesHeld: Int? = null,
    val moment: String? = null,
)

/** One bar of the histogram: its solve [count], relative [height] (0 for an empty bin) and its time [range] label. */
internal data class HeroBar(val count: Int, val height: Float, val range: String)

/**
 * One scope's chart and words. [bars] are contiguous time ranges (empty ones keep their slot);
 * [edges] are the round times between them, one more than [bars] (as many when the last bar is the open slow tail); [youBin] holds this solve.
 * [description] reads the chart aloud.
 */
internal data class HeroScopeView(
    val heading: HeroHeading,
    val caption: String,
    val verdict: Emphasized,
    val notes: List<Emphasized>,
    val bars: List<HeroBar>,
    val edges: List<String>,
    val youBin: Int,
    val description: String,
)

/**
 * The histogram block: one [HeroScopeView] per scope, opening on [defaultScope]; [playAgain] draws this solve's bar dashed.
 * [switchable]: the scopes hold a different number of solves, so the heading carries a chip that swaps them.
 */
internal data class HeroSpread(
    val views: Map<SpreadScope, HeroScopeView>,
    val defaultScope: SpreadScope,
    val playAgain: Boolean,
    val switchable: Boolean,
)

/**
 * The rank row: a bold [value] and its [sub] line, or a sign-in prompt. A [connections] line, when present, gives the
 * place among players and LinkedIn connections under the PuzzleBox rank. On a play again the [sub] says the place is
 * the player's counting solve of that day.
 */
internal sealed interface HeroRank {
    data class Place(val value: String, val sub: String, val connections: String? = null) : HeroRank
    data class SignIn(val text: String) : HeroRank
}

/**
 * What the Results hero page shows, derived once from the facts, so the cards only draw.
 * A null [pill], [bestLine], [streak], [spread] or [rank] hides that element. [bestImproved]
 * paints the best line in the game's accent (a new best); [celebrate] is the entrance's
 * pulse; [burst] adds the burst (a new best only). [moments] are the moment card's rows, strongest first, at most
 * three, none for a practice puzzle or a play again; [challenge] is the Challenge row's friend, none when the
 * [primary] already is that challenge. [bestSubLine] is the small line under the comparison ("Best on this puzzle 1:41");
 * [compare] draws the comparison line larger, as the same-puzzle comparison does. [compareColumns] (Result hero polish) is
 * the previous and best solve as two labelled columns; it replaces [bestLine] and [bestSubLine], which are then null.
 */
internal data class ResultHeroModel(
    val title: String,
    val dateLine: String,
    val timeMs: Long,
    val pill: String?,
    val bestLine: String?,
    val bestImproved: Boolean,
    val chips: List<ResultHeroChip>,
    val streak: HeroStreak?,
    val spread: HeroSpread?,
    val rank: HeroRank?,
    val celebrate: Boolean,
    val burst: Boolean,
    val primary: ResultPrimaryAction?,
    val moments: List<HeroMoment>,
    val challenge: ResultPrimaryAction.ChallengeFriend?,
    /** The sign-in invitation under the time; null when the player is signed in or the line is off. */
    val signInLine: String? = null,
    val bestSubLine: String? = null,
    val compare: Boolean = false,
    val compareColumns: HeroCompare? = null,
)

/**
 * The model for [facts]. A practice puzzle counts nothing: no pill, streak, spread or rank. A play-again
 * counts nothing either, so it has no pill, celebration or burst, but its side card matches a counting solve's:
 * the streak is the player's current one (the insights leave this play-again out of it), the spread is
 * drawn with this solve dashed, and the rank is the player's counting solve's place on that day's board, labelled
 * so ([heroRankSubPlayAgain], [HERO_SIGN_IN_PLAY_AGAIN_LINE]) and never read as this play-again's. The streak,
 * spread and rank wait for their data, so none shows a placeholder (a player not on that day's board has no rank
 * row); the Average chip hides on a first solve.
 * A non-null [chip] replaces the Today chip with the caller's own.
 */
internal fun resultHeroModel(
    facts: ResultHeroFacts,
    locale: Locale = Locale.getDefault(),
    chip: ResultHeroChip? = null,
): ResultHeroModel {
    val counts = !facts.practice && !facts.playAgain
    val insights = facts.insights.takeUnless { facts.practice }
    val verdict = facts.verdict.takeUnless { facts.practice }
    val pill = heroPill(verdict).takeIf { counts }
    val columns = facts.samePuzzle?.takeIf { it.columns && !facts.practice }
        ?.let { heroSamePuzzleCompare(it, facts.rankOrder, facts.timeMs, facts.hintsUsed) }
    return ResultHeroModel(
        title = resultTitle(facts.game, facts.puzzleNumber, facts.practice),
        dateLine = facts.dateLine,
        timeMs = facts.timeMs,
        pill = pill,
        bestLine = bestLine(facts, verdict).takeIf { columns == null },
        bestSubLine = facts.samePuzzle?.let { heroSamePuzzleBest(it, facts.rankOrder) }.takeUnless { facts.practice || columns != null },
        compare = facts.samePuzzle != null,
        compareColumns = columns,
        bestImproved = counts && verdict == RecordVerdict.NewBest,
        chips = chips(facts, insights, chip),
        streak = insights?.let { streak(it, locale) },
        spread = insights?.let { spread(facts, it, locale) },
        rank = if (facts.practice) null else rank(facts.rank, facts.playAgain),
        celebrate = counts && (verdict == RecordVerdict.NewBest || verdict == RecordVerdict.FirstSolve),
        burst = counts && verdict == RecordVerdict.NewBest,
        primary = facts.primary,
        moments = if (counts) heroMoments(facts.moments) else emptyList(),
        challenge = visibleChallenge(facts.primary, facts.challenge),
        signInLine = HERO_SIGN_IN_LINE.takeIf { facts.signInLine && counts && facts.rank == ResultRank.SignedOut },
    )
}

/**
 * The line under the time. With the same-puzzle comparison on ([ResultHeroFacts.samePuzzle]) an earlier solve of this
 * puzzle is what the solve is compared with, whatever the game-wide verdict (it may be unknown after process death),
 * and nothing shows until those solves are read; without an earlier solve, or with the comparison off, it is the
 * game-wide wording. A practice puzzle has none.
 */
private fun bestLine(facts: ResultHeroFacts, verdict: RecordVerdict?): String? {
    val same = facts.samePuzzle
    return when {
        facts.practice -> null
        same != null && !same.read -> null
        same?.previous != null -> {
            val line = heroSamePuzzleLine(same.previous, same.previousFromLinkedIn, facts.rankOrder, facts.timeMs, facts.hintsUsed)
            if (facts.playAgain) "${PlayAgainCopy.LABEL} · $line" else line
        }
        else -> heroBestLine(
            verdict = if (facts.playAgain) RecordVerdict.PlayAgain else verdict,
            previousBest = facts.previousBest,
            order = facts.rankOrder,
            timeMs = facts.timeMs,
            hintsUsed = facts.hintsUsed,
        )
    }
}

private fun chips(facts: ResultHeroFacts, insights: ResultInsights?, supplied: ResultHeroChip?): List<ResultHeroChip> = buildList {
    add(
        ResultHeroChip(
            tag = "result.hero.chip.hints",
            value = if (facts.hintsUsed == 0 && facts.community == null) "None" else "${facts.hintsUsed}",
            label = if (facts.hintsUsed == 1) "Hint" else "Hints",
        ),
    )
    if (facts.community != null) {
        addAll(facts.community.chips)
    } else if (supplied != null) {
        add(supplied)
    } else if (facts.doneToday != null && facts.total != null && facts.total > 0) {
        add(ResultHeroChip("result.hero.chip.today", "${facts.doneToday} of ${facts.total}", "Today"))
    }
    insights?.averageMs?.let { add(ResultHeroChip("result.hero.chip.average", formatDuration(it), "Average")) }
}

private fun streak(insights: ResultInsights, locale: Locale) = HeroStreak(
    days = insights.streak,
    title = heroStreakTitle(insights.streak),
    sub = insights.freezes?.let(::heroFreezeSub) ?: heroStreakSub(insights.streak),
    week = insights.week.map { HeroWeekDot(weekdayInitial(it.date, locale), it.solved, it.today, it.frozen) },
    freezesHeld = insights.freezes?.heldWhileStreak(insights.streak),
    moment = insights.freezes?.let { freezes -> insights.moment?.let { heroFreezeMoment(it, freezes.held, locale) } },
)

private fun spread(facts: ResultHeroFacts, insights: ResultInsights, locale: Locale): HeroSpread {
    val weekday = heroWeekdayName(insights.weekday.day, locale)
    val switchable = insights.spread.solves != insights.weekday.spread.solves
    val views = SpreadScope.entries.associateWith { scope ->
        val spread = if (scope == SpreadScope.Weekday) insights.weekday.spread else insights.spread
        val heading = heroSpreadHeading(scope, spread.solves, facts.game, weekday, switchable)
        HeroScopeView(
            heading = heading,
            caption = heroSpreadCaption(scope, spread.isFirstSolve, weekday, switchable),
            verdict = heroSpreadVerdict(scope, facts.rankOrder, spread, facts.game, weekday, facts.playAgain),
            notes = listOfNotNull(
                heroRankNote(facts.rankOrder, spread),
                heroWeekdayNote(insights.spread, insights.weekday.spread, weekday, insights.weekday.fastest),
            ),
            bars = spread.bins.map { HeroBar(it.count, it.height, if (it.open) heroOpenBinRange(it.fromMs) else heroBinRange(it.fromMs, it.toMs)) },
            edges = spread.bins.map { formatDuration(it.fromMs) } + listOfNotNull(spread.bins.last().takeUnless { it.open }?.let { formatDuration(it.toMs) }),
            youBin = spread.youBin,
            description = heroChartDescription(heading.text, spread.bins.size, facts.timeMs),
        )
    }
    val defaultScope = if (switchable) insights.defaultScope else SpreadScope.All
    return HeroSpread(views = views, defaultScope = defaultScope, playAgain = facts.playAgain, switchable = switchable)
}

private fun rank(rank: ResultRank, playAgain: Boolean): HeroRank? = when (rank) {
    ResultRank.Hidden -> null
    ResultRank.SignedOut -> HeroRank.SignIn(if (playAgain) HERO_SIGN_IN_PLAY_AGAIN_LINE else HERO_SIGN_IN_LINE)
    is ResultRank.Ranked -> HeroRank.Place(
        value = heroRankValue(rank.position, rank.count, rank.topPercent),
        sub = if (playAgain) heroRankSubPlayAgain(rank.count) else heroRankSub(rank.count),
        connections = rank.withConnections?.let { heroRankConnections(it.position, it.count) },
    )
}
