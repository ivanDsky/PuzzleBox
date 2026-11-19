package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.data.insights.RecapDismissals
import com.ivandsky.puzzlebox.home.RecapSlot
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveDueRecap
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The [RecapSlot] Home hosts, live: it follows the day's first solve, the puzzle-day rollover and a dismissal as
 * they happen, with no polling. While [InsightsFlag.MonthlyRecap] is not in [enabled] nothing is observed at all. The
 * dismissals are read before any card shows, so a dismissed card never flashes back on a cold start. See [recapSlot].
 */
@Composable
internal fun rememberRecapSlot(
    enabled: Set<ReleaseFlagKey>,
    navigator: Navigator,
    observeDueRecap: ObserveDueRecap,
    dismissals: RecapDismissals,
): RecapSlot? {
    val on = InsightsFlag.MonthlyRecap in enabled
    val due by remember(on, observeDueRecap) { if (on) observeDueRecap() else flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)
    val dismissed by remember(dismissals) { dismissals.observe() }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val loaded = dismissed
    return remember(enabled, due, loaded, navigator, dismissals) {
        loaded?.let {
            recapSlot(
                enabled = enabled,
                due = due,
                dismissed = it,
                navigator = navigator,
                dismiss = { periodId -> scope.launch { dismissals.dismiss(periodId) } },
            )
        }
    }
}
