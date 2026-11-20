package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.home.RecapSlot
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.domain.recap.DueRecap
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator
import java.time.LocalDate
import java.time.Month

/**
 * Home's recap card, behind the recap flags (ADR-0022): the first of the [due] recaps, the year before the month, whose
 * flag is in [enabled] ([InsightsFlag.YearRecap] for a year, [InsightsFlag.MonthlyRecap] for a month) and whose period
 * is not in [dismissed]; null otherwise, and Home draws no card. On Jan 1–3 both can be due: Home shows the year, and
 * the month once the year is dismissed. Opening pushes that period's [InsightsRecapKey] as a layer page and dismissing
 * reports its period id to [dismiss]. The one gate, so a card never shows before its flag turns on (A2, A3).
 *
 * A null [due] is a list still loading: inside a recap window on the puzzle day [today] (see [inRecapWindow]) the slot is
 * [RecapSlot.Loading], a skeleton, and anywhere else null, so no tile, no skeleton and no reserved space. A period in
 * [opened] is a recap whose story was watched.
 */
internal fun recapSlot(
    enabled: Set<ReleaseFlagKey>,
    due: List<DueRecap>?,
    dismissed: Set<String>,
    opened: Set<String>,
    today: LocalDate?,
    navigator: Navigator,
    dismiss: (periodId: String) -> Unit,
): RecapSlot? {
    if (due == null) return RecapSlot.Loading.takeIf { enabled.any { it == InsightsFlag.MonthlyRecap || it == InsightsFlag.YearRecap } && inRecapWindow(today) }
    val shown = due.firstOrNull { it.flag() in enabled && it.periodId !in dismissed } ?: return null
    return RecapSlot.Due(
        periodId = shown.periodId,
        label = shown.label,
        yearly = shown.yearly,
        solved = shown.solved,
        solvedByGame = shown.solvedByGame,
        months = shown.months,
        opened = shown.periodId in opened,
        onOpen = { navigator.pushLayer(InsightsRecapKey(shown.periodId)) },
        onDismiss = { dismiss(shown.periodId) },
    )
}

/**
 * Whether [today], the puzzle day and never the device's date, is where a recap can be due: days 1–3 of a month (the
 * month just ended) and Jan 1–7 (the year just ended), the windows `ObserveRecapAvailability.due` offers. Null while the
 * day is unknown.
 */
internal fun inRecapWindow(today: LocalDate?): Boolean =
    today != null && (today.dayOfMonth <= MONTH_WINDOW_DAYS || (today.month == Month.JANUARY && today.dayOfMonth <= YEAR_WINDOW_DAYS))

private const val MONTH_WINDOW_DAYS = 3
private const val YEAR_WINDOW_DAYS = 7

private fun DueRecap.flag(): InsightsFlag = if (yearly) InsightsFlag.YearRecap else InsightsFlag.MonthlyRecap
