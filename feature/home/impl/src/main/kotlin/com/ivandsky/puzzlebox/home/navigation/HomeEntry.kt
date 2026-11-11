package com.ivandsky.puzzlebox.home.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.HomeRoute
import com.ivandsky.puzzlebox.home.HomeDefaultTitle
import com.ivandsky.puzzlebox.home.HomeSummaries
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey

/**
 * Home's route: a Today tile or the CTA pushes that game's [GameKey] once on Home's stack
 * (A6, A10, N1/N8/N9: back returns to Home), "See all" pushes [HistoryKey] on Home's
 * stack (or runs [onOpenHistory] when given), and the account card selects the Profile tab. A non-null [summaries] adds the rank
 * and duels rows to Today; null is Today without them.
 */
fun EntryProviderScope<NavKey>.homeEntry(
    navigator: Navigator,
    layout: HomeLayout,
    hideOwnedElsewhere: Boolean = false,
    summaries: HomeSummaries? = null,
    onOpenHistory: (() -> Unit)? = null,
    title: String? = null,
) {
    entry<HomeKey> {
        HomeRoute(
            layout = layout,
            hideOwnedElsewhere = hideOwnedElsewhere,
            title = title ?: HomeDefaultTitle,
            summaries = summaries,
            onOpenGame = { id -> navigator.pushOnce(GameKey(id.value)) },
            // A6: the account card is a tab switch, not a push. Home's stack is untouched,
            // so Home (bar or back from the Profile root) returns to it as it was.
            onOpenProfile = { navigator.selectTab(ProfileKey) },
            onOpenHistory = onOpenHistory ?: { navigator.pushOnce(HistoryKey()) },
        )
    }
}
