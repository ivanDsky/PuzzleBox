package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.home.RecapSlot
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.domain.recap.DueRecap
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Home's recap card, behind the recap flags (ADR-0022): the first of the [due] recaps, the year before the month, whose
 * flag is in [enabled] ([InsightsFlag.YearRecap] for a year, [InsightsFlag.MonthlyRecap] for a month) and whose period
 * is not in [dismissed]; null otherwise, and Home draws no card. On Jan 1–3 both can be due: Home shows the year, and
 * the month once the year is dismissed. Opening pushes that period's [InsightsRecapKey] as a layer page and dismissing
 * reports its period id to [dismiss]. The one gate, so a card never shows before its flag turns on (A2, A3).
 */
internal fun recapSlot(
    enabled: Set<ReleaseFlagKey>,
    due: List<DueRecap>,
    dismissed: Set<String>,
    navigator: Navigator,
    dismiss: (periodId: String) -> Unit,
): RecapSlot? {
    val shown = due.firstOrNull { it.flag() in enabled && it.periodId !in dismissed } ?: return null
    return RecapSlot(
        label = shown.label,
        onOpen = { navigator.pushLayer(InsightsRecapKey(shown.periodId)) },
        onDismiss = { dismiss(shown.periodId) },
    )
}

private fun DueRecap.flag(): InsightsFlag = if (yearly) InsightsFlag.YearRecap else InsightsFlag.MonthlyRecap
