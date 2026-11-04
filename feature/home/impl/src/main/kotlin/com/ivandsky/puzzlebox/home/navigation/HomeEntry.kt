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
 * Home's route: the Today card pushes [PlayKey] (today's board is fetched fresh by the play
 * route), the history teaser pushes [HistoryKey], and the account card opens [ProfileKey].
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
            onPlayToday = { navigator.navigateOnce(PlayKey) },
            // Bug (2026-11-02, user report): tab selection clears the stack — correct for
            // bar/rail tab switches, but this is a content affordance; clearing left
            // Profile as the sole entry, so back exited the app. Push, don't clear.
            onOpenProfile = { navigator.navigateOnce(ProfileKey) },
            onOpenHistory = { navigator.navigateOnce(HistoryKey) },
        )
    }
}
