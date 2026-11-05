package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.gameui.WeekStrip
import com.ivandsky.puzzlebox.model.GameId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How many calendar weeks History shows above the list (spec A18). */
internal const val HISTORY_WEEKS = 4

/**
 * The last [HISTORY_WEEKS] calendar weeks (Monday to Sunday), newest week first, each
 * day carrying the brands of [rows]' numbered solves in [order] — practice never fills
 * a dot, the same rule as the streak. Days after [today] stay empty.
 */
internal fun historyWeeks(today: LocalDate, rows: List<HistoryDayRow>, order: List<GameId>): List<List<WeekDay>> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rank = order.withIndex().associate { (index, id) -> id to index }
    val solved: Map<LocalDate, List<GameBrand>> = rows
        .filter { it.counts }
        .sortedBy { rank[it.gameId] ?: Int.MAX_VALUE }
        .groupBy({ it.day }, { it.brand })
    return (0 until HISTORY_WEEKS).map { back ->
        val start = monday.minusWeeks(back.toLong())
        (0L until DayOfWeek.entries.size).map { offset ->
            val date = start.plusDays(offset)
            WeekDay(date = date, solved = solved[date].orEmpty(), isToday = date == today)
        }
    }
}

/**
 * The stacked [weeks] in one card (spec A18): each day's dots out of [total] — every
 * game on All, one dot on a game's tab.
 */
@Composable
internal fun HistoryWeekStrips(
    weeks: List<List<WeekDay>>,
    total: Int,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth().testTag("history.weeks"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            weeks.forEach { week -> WeekStrip(days = week, total = total) }
        }
    }
}
