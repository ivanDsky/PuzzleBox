package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.insights.api.InsightsKey
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Profile's way into Insights, behind [InsightsFlag.MonthlyRecap] (ADR-0022): while the flag is in [enabled] it opens the
 * hub as a layer page; while it is not, null, and Profile draws no row. The one gate, so no entry point leaks Insights
 * before the flag turns on.
 */
internal fun insightsOpener(enabled: Set<ReleaseFlagKey>, navigator: Navigator): (() -> Unit)? =
    if (InsightsFlag.MonthlyRecap in enabled) ({ navigator.pushLayer(InsightsKey) }) else null
