package com.ivandsky.puzzlebox.home.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.HomeRoute
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey

/**
 * Home's route: the Today card selects the Play tab (today's board is fetched fresh by the
 * play route), the history teaser pushes [HistoryKey] on Home's stack, and the account card
 * selects the Profile tab.
 */
fun EntryProviderScope<NavKey>.homeEntry(
    navigator: Navigator,
    layout: HomeLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
) {
    entry<HomeKey> {
        HomeRoute(
            layout = layout,
            themeMode = themeMode,
            onSetThemeMode = onSetThemeMode,
            onPlayToday = { navigator.selectTab(PlayKey) },
            // A6: the account card is a tab switch, not a push. Home's stack is untouched,
            // so Home (bar or back from the Profile root) returns to it as it was.
            onOpenProfile = { navigator.selectTab(ProfileKey) },
            onOpenHistory = { navigator.pushOnce(HistoryKey) },
        )
    }
}
