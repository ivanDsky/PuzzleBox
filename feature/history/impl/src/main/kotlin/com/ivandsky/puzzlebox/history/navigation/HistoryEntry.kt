package com.ivandsky.puzzlebox.history.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.HistoryRoute
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.ReplayKey
import java.util.Base64

/** History's route: a replayed day pushes [ReplayKey] carrying the day's session blob. */
fun EntryProviderScope<NavKey>.historyEntry(
    navigator: Navigator,
    layout: HistoryLayout,
) {
    entry<HistoryKey> {
        HistoryRoute(
            layout = layout,
            onBack = { navigator.goBack() },
            onReplay = { session ->
                navigator.navigate(ReplayKey(Base64.getEncoder().encodeToString(session)))
            },
        )
    }
}
