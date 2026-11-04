package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.app.CatalogScreen
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.navigation.historyEntry
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.navigation.homeEntry
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.navigation.playEntry
import com.ivandsky.puzzlebox.play.navigation.replayEntry
import com.ivandsky.puzzlebox.play.navigation.resultEntry
import com.ivandsky.puzzlebox.profile.navigation.profileEntry

/**
 * The app's route table (ticket 17): one entry per [NavKey], every destination real —
 * each feature contributes its own entries; the shell owns only the catalog page.
 * Building it is a plain lookup construction — no composition — so NavRoutesTest
 * resolves each key against it and fails fast if a pushed key ever lacks a route.
 *
 * Loop wiring: Home's Today card pushes Play; its history teaser pushes History; history
 * rows push Replay with the day's session blob; the account card opens the Profile
 * account surface; Profile's catalog link pushes [CatalogKey] as a deliberate deep page —
 * NavDisplay's default back pop returns it to Profile, including after process-death restore.
 */
internal fun puzzleBoxEntryProvider(
    navigator: Navigator,
    homeLayout: HomeLayout,
    playLayout: PlayLayout,
    historyLayout: HistoryLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    homeEntry(navigator, homeLayout, themeMode, onSetThemeMode)
    historyEntry(navigator, historyLayout)
    replayEntry(navigator, playLayout)
    playEntry(navigator, playLayout)
    resultEntry(navigator)
    profileEntry(onOpenCatalog = { navigator.navigate(CatalogKey) })
    entry<CatalogKey> { CatalogScreen() }
}
