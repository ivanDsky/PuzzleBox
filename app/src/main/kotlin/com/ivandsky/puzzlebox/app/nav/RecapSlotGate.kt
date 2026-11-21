package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.home.RecapSlot
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.api.RecapWindow
import com.ivandsky.puzzlebox.insights.domain.recap.DueRecap
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator
import java.time.LocalDate

/**
 * Home's recap card, behind the recap flags (ADR-0022): the first of the [due] recaps, the year before the month, whose
 * flag is in [enabled] ([InsightsFlag.YearRecap] for a year, [InsightsFlag.MonthlyRecap] for a month) and whose period
 * is not in [dismissed]; null otherwise, and Home draws no card. On Jan 1–3 both can be due: Home shows the year, and
 * the month once the year is dismissed. Opening pushes that period's [InsightsRecapKey] as a layer page and dismissing
 * reports its period id to [dismiss]. The one gate, so a card never shows before its flag turns on (A2, A3).
 *
 * A null [due] is a list still loading: inside a recap window on the puzzle day [today] (see [RecapWindow]) the slot is
 * [RecapSlot.Loading], a skeleton, and anywhere else null, so no tile, no skeleton and no reserved space. A period in
 * [opened] is a recap whose story was watched. Until [dismissed] and [opened] have both loaded the slot is null, so a
 * dismissed tile never flashes in and a watched one never shows New first.
 */
internal fun recapSlot(
    enabled: Set<ReleaseFlagKey>,
    due: List<DueRecap>?,
    dismissed: Set<String>?,
    opened: Set<String>?,
    today: LocalDate?,
    navigator: Navigator,
    dismiss: (periodId: String) -> Unit,
): RecapSlot? {
    if (dismissed == null || opened == null) return null
    if (due == null) return RecapSlot.Loading.takeIf { enabled.any { it == InsightsFlag.MonthlyRecap || it == InsightsFlag.YearRecap } && today != null && RecapWindow.isOpen(today) }
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
        liftKey = InsightsRecapKey(shown.periodId),
    )
}

private fun DueRecap.flag(): InsightsFlag = if (yearly) InsightsFlag.YearRecap else InsightsFlag.MonthlyRecap
