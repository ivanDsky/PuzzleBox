package com.ivandsky.puzzlebox.home.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.HomeRoute
import com.ivandsky.puzzlebox.home.HomeSummaries
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey

/**
 * Home's route: a Today tile or the CTA pushes that game's [GameKey] once on Home's stack
 * (A6, A10, N1/N8/N9: back returns to Home), and "See all" runs [onOpenHistory], which
 * the app points at the History tab root. [summaries] adds the rank and duels rows to
 * Today; [title] is the header's title.
 */
fun EntryProviderScope<NavKey>.homeEntry(
    navigator: Navigator,
    layout: HomeLayout,
    summaries: HomeSummaries,
    onOpenHistory: () -> Unit,
    title: String,
) {
    entry<HomeKey> {
        HomeRoute(
            layout = layout,
            title = title,
            summaries = summaries,
            onOpenGame = { id -> navigator.pushOnce(GameKey(id.value)) },
            onOpenHistory = onOpenHistory,
        )
    }
}
