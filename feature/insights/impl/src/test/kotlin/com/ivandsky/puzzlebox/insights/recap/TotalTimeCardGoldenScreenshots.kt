package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET
import com.ivandsky.puzzlebox.insights.domain.recap.DayTally
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.settleAndCapture
import com.ivandsky.puzzlebox.insights.showGolden
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Total time card in its five states on phone and tablet, light and dark, over the contract's data. Goldens in
 * `snapshots/recap/`: default (October 2026, 3h 47m, 96 puzzles), empty (one 72 s solve), longest (99h 59m), scrubbing
 * (a finger on 17 Oct) and year (51 weeks of 2026 so far).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TotalTimeCardGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time default phone light`() = capture("default", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time default phone dark`() = capture("default", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time default tablet light`() = capture("default", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time default tablet dark`() = capture("default", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time empty phone light`() = capture("empty", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time empty phone dark`() = capture("empty", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time empty tablet light`() = capture("empty", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time empty tablet dark`() = capture("empty", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time longest phone light`() = capture("longest", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time longest phone dark`() = capture("longest", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time longest tablet light`() = capture("longest", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time longest tablet dark`() = capture("longest", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time scrubbing phone light`() = capture("scrubbing", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time scrubbing phone dark`() = capture("scrubbing", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time scrubbing tablet light`() = capture("scrubbing", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time scrubbing tablet dark`() = capture("scrubbing", "tablet", dark = true)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time year phone light`() = capture("year", "phone", dark = false)

    @Config(qualifiers = GOLDEN_PHONE)
    @Test fun `total-time year phone dark`() = capture("year", "phone", dark = true)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time year tablet light`() = capture("year", "tablet", dark = false)

    @Config(qualifiers = GOLDEN_TABLET)
    @Test fun `total-time year tablet dark`() = capture("year", "tablet", dark = true)

    private fun capture(state: String, device: String, dark: Boolean) {
        val (title, cards) = story(state)
        val ui = RecapUiState.Ready(period = if (state == "year") RecapFixtures.year2026 else RecapFixtures.november, title = title, cards = cards, initialPage = 0)
            .onCard(cards.indexOfFirst { it is RecapCard.TotalTime })
        rule.showGolden(dark = dark) {
            RecapScreen(ui, title, gameName, onPage = {}, onWatchReplay = {}, onClose = {})
        }
        if (state == "scrubbing") {
            rule.onNodeWithTag("recap.totaltime.river").performTouchInput { down(Offset(width * (SCRUBBED_DAY - 0.5f) / OCTOBER_DAYS, height / 2f)) }
        }
        rule.settleAndCapture("recap", "total-time-$state", device, dark)
    }

    private fun story(state: String): Pair<String, List<RecapCard>> = when (state) {
        "empty" -> OCTOBER to FILLERS.take(4) + totalTime(listOf(DayTally(OCT.atDay(3), 1, 72_000, 1)), 72_000, 1)
        "longest" -> OCTOBER to FILLERS.take(5) + longest() + RecapFixtures.moments
        "year" -> YEAR_TITLE to List(20) { FILLERS[it % FILLERS.size] }.toMutableList().also { it[5] = year() }
        else -> OCTOBER to FILLERS.take(5) + october() + RecapFixtures.moments
    }

    private fun totalTime(days: List<DayTally>, totalMs: Long, solved: Int, period: RecapPeriod = RecapPeriod.Month(OCT)) =
        RecapCard.TotalTime(totalMs, solved, days, period)

    /** 227 minutes over 96 puzzles; no play on 2 and 30 Oct; the busiest day is Sun 11 Oct (16m, 5 puzzles). */
    private fun october(): RecapCard.TotalTime {
        val days = OCTOBER_MINUTES.indices.filter { OCTOBER_MINUTES[it] > 0 }.map { DayTally(OCT.atDay(it + 1), OCTOBER_SOLVES[it], OCTOBER_MINUTES[it] * MINUTE, hintFree = OCTOBER_SOLVES[it] - 1) }
        require(days.sumOf { it.timeMs } == 13_620_000L && days.sumOf { it.solved } == 96)
        return totalTime(days, 13_620_000, 96)
    }

    /** Every day played about 3h10m, Fri 30 Oct the longest at 5h 1m: 99h 59m over 155 puzzles. */
    private fun longest(): RecapCard.TotalTime {
        val days = (1..OCTOBER_DAYS).map { DayTally(OCT.atDay(it), solved = 5, timeMs = (if (it == 30) 301 else if (it in 3..4) 189 else 190) * MINUTE, hintFree = 4) }
        require(days.sumOf { it.timeMs } == 359_940_000L && days.sumOf { it.solved } == 155)
        return totalTime(days, 359_940_000, 155)
    }

    /** 51 Monday weeks of 2026 so far (the first from 1 Jan): 61h 18m over 625 puzzles, the busiest week 1h 49m. */
    private fun year(): RecapCard.TotalTime {
        val days = YEAR_MINUTES.indices.map { DayTally(if (it == 0) LocalDate.of(2026, 1, 1) else LocalDate.of(2026, 1, 5).plusWeeks(it - 1L), YEAR_SOLVES[it], YEAR_MINUTES[it] * MINUTE, hintFree = YEAR_SOLVES[it] - 1) }
        require(days.sumOf { it.timeMs } == 220_680_000L && days.sumOf { it.solved } == 625)
        return totalTime(days, 220_680_000, 625, RecapPeriod.Year(2026))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val OCTOBER = "October 2026"
        const val YEAR_TITLE = "2026 so far"
        const val OCTOBER_DAYS = 31
        const val SCRUBBED_DAY = 17
        val OCT: YearMonth = YearMonth.of(2026, 10)
        val FILLERS = listOf(RecapFixtures.solved, RecapFixtures.fastest, RecapFixtures.streak, RecapFixtures.moments, RecapFixtures.friendsLoaded)
        val OCTOBER_MINUTES = listOf(5, 0, 10, 9, 3, 12, 8, 3, 5, 10, 16, 9, 6, 2, 7, 11, 14, 6, 5, 9, 6, 11, 5, 8, 14, 6, 3, 13, 5, 0, 6)
        val OCTOBER_SOLVES = listOf(3, 0, 4, 4, 2, 5, 4, 1, 3, 4, 5, 4, 3, 1, 4, 5, 5, 3, 2, 4, 2, 5, 2, 4, 4, 3, 1, 5, 2, 0, 2)
        val YEAR_MINUTES = listOf(69, 100, 92, 88, 92, 58, 32, 48, 56, 59, 91, 105, 86, 74, 70, 40, 30, 60, 75, 77, 105, 105, 68, 55, 54, 36, 42, 82, 92, 87, 100, 86, 47, 109, 53, 45, 63, 104, 100, 83, 84, 65, 30, 40, 64, 64, 85, 105, 93, 66, 64)
        val YEAR_SOLVES = listOf(13, 16, 15, 15, 16, 11, 6, 8, 10, 10, 16, 16, 14, 13, 12, 8, 6, 10, 13, 13, 16, 16, 12, 10, 9, 8, 8, 14, 15, 15, 16, 14, 8, 17, 9, 9, 11, 16, 16, 14, 15, 11, 6, 7, 11, 12, 14, 16, 16, 11, 12)
    }
}
