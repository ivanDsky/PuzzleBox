package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.app.CatalogScreen
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.create.navigation.publishedPuzzleEntry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.navigation.historyEntry
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.navigation.homeEntry
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.navigation.playEntry
import com.ivandsky.puzzlebox.play.navigation.resultEntry
import com.ivandsky.puzzlebox.profile.navigation.profileEntry

/**
 * The app's route table (ticket 17): one entry per [NavKey], every destination real —
 * each feature contributes its own entries; the shell owns only the catalog page.
 * Building it is a plain lookup construction — no composition — so NavRoutesTest
 * resolves each key against it and fails fast if a pushed key ever lacks a route.
 *
 * Loop wiring: Home's Today card selects the Play tab; its history teaser pushes History
 * on Home's stack, and the games hub's "Older puzzles" pushes it on Play's stack; history
 * rows push the day's game key; the account card
 * selects the Profile tab, leaving Home's stack as it was; a Profile game row pushes that
 * game's [HistoryKey] and Profile's (debug-only) catalog link pushes [CatalogKey], both on
 * Profile's stack. The Create tab's New puzzle and its draft rows push that draft's
 * Studio on Create's stack, and its published rows that puzzle's page. Back pops the current tab's stack ([Navigator.pop]),
 * including after process-death restore. A [codeDraft] (a tapped link's text that isn't a
 * code) opens the Play library's code sheet holding it.
 */
internal fun puzzleBoxEntryProvider(
    navigator: Navigator,
    homeLayout: HomeLayout,
    playLayout: PlayLayout,
    historyLayout: HistoryLayout,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    homeEntry(navigator, homeLayout)
    historyEntry(navigator, historyLayout)
    playEntry(
        navigator,
        playLayout,
        onOpenHistory = { navigator.pushOnce(HistoryKey()) },
        codeDraft = codeDraft,
        onCodeDraftTaken = onCodeDraftTaken,
    )
    resultEntry(navigator)
    createEntry(navigator)
    editorEntry(navigator, playLayout)
    publishedPuzzleEntry(navigator, playLayout)
    profileEntry(
        onOpenHistory = { game -> navigator.pushOnce(HistoryKey(game.value)) },
        onOpenCatalog = { navigator.push(CatalogKey) },
    )
    entry<CatalogKey> { CatalogScreen(openGame = { navigator.pushOnce(GameKey(it)) }) }
}
