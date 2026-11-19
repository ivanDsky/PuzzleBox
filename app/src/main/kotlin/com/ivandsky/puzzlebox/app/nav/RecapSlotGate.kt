package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.home.RecapSlot
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.domain.recap.DueRecap
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Home's recap card, behind [InsightsFlag.MonthlyRecap] (ADR-0022): the slot for the [due] recap while the flag is in
 * [enabled], the recap is due and its period is not in [dismissed]; null otherwise, and Home draws no card. Opening
 * pushes that period's [InsightsRecapKey] as a layer page and dismissing reports its period id to [dismiss].
 * The one gate, so the card never shows before the flag turns on (A2, A3).
 */
internal fun recapSlot(
    enabled: Set<ReleaseFlagKey>,
    due: DueRecap?,
    dismissed: Set<String>,
    navigator: Navigator,
    dismiss: (periodId: String) -> Unit,
): RecapSlot? =
    if (InsightsFlag.MonthlyRecap !in enabled || due == null || due.periodId in dismissed) {
        null
    } else {
        RecapSlot(
            label = due.label,
            onOpen = { navigator.pushLayer(InsightsRecapKey(due.periodId)) },
            onDismiss = { dismiss(due.periodId) },
        )
    }
