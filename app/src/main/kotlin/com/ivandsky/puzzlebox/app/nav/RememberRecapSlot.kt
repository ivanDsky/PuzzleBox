package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.insights.RecapDismissals
import com.ivandsky.puzzlebox.data.insights.RecapOpenings
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.home.RecapSlot
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveDueRecap
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The [RecapSlot] Home hosts, live: it follows the day's first solve, the puzzle-day rollover and a dismissal as
 * they happen, with no polling. While neither [InsightsFlag.MonthlyRecap] nor [InsightsFlag.YearRecap] is in [enabled]
 * nothing is observed at all. The dismissals and the openings are read before any card shows (see [recapSlot]).
 */
@Composable
internal fun rememberRecapSlot(
    enabled: Set<ReleaseFlagKey>,
    navigator: Navigator,
    observeDueRecap: ObserveDueRecap,
    dismissals: RecapDismissals,
    openings: RecapOpenings,
    calendar: GameCalendar,
    games: GameRegistry,
): RecapSlot? {
    val on = InsightsFlag.MonthlyRecap in enabled || InsightsFlag.YearRecap in enabled
    val due by remember(on, observeDueRecap) { if (on) observeDueRecap() else flowOf(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = null)
    val today by remember(on, calendar, games) {
        val game = games.all.firstOrNull()?.definition?.id
        if (on && game != null) calendar.observeToday(game) else flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val dismissed by remember(dismissals) { dismissals.observe() }.collectAsStateWithLifecycle(initialValue = null)
    val opened by remember(openings) { openings.observe() }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    return remember(enabled, due, today, dismissed, opened, navigator, dismissals) {
        recapSlot(
            enabled = enabled,
            due = due,
            dismissed = dismissed,
            opened = opened,
            today = today,
            navigator = navigator,
            dismiss = { periodId -> scope.launch { dismissals.dismiss(periodId) } },
        )
    }
}
