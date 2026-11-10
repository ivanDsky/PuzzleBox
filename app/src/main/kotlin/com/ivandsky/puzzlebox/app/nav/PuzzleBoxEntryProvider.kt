package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.app.CatalogScreen
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.navigation.competeEntry
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.create.navigation.publishedPuzzleEntry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.history.navigation.historyEntry
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.HomeSummaries
import com.ivandsky.puzzlebox.home.navigation.homeEntry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.GamesTab
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
 *
 * With [navV2] on, every screen gets its NavV2 form and each content type one owner
 * (ADR-0027): Today's rank and duels summaries, the game page's "Your history" row and
 * Result's "Full board" row open their owner (a game page or Games section through
 * [libraryRequests], History's tab root through [openOwner]); Today hides what is owned
 * elsewhere, the game page draws its board, the tablet times card signs in in place, and
 * Profile is slim with its own back. Off, every screen keeps its old form.
 */
internal fun puzzleBoxEntryProvider(
    navigator: Navigator,
    homeLayout: HomeLayout,
    playLayout: PlayLayout,
    historyLayout: HistoryLayout,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
    onOpenDuel: ((DuelKey) -> Unit)? = null,
    navV2: Boolean = false,
    libraryRequests: LibraryRequests = LibraryRequests(),
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    homeEntry(
        navigator,
        homeLayout,
        hideOwnedElsewhere = navV2,
        summaries = HomeSummaries(
            onOpenRank = { libraryRequests.openGamePage(navigator, it.value) },
            onOpenDuels = { libraryRequests.openGamesSection(navigator, GamesTab.Duels) },
            duelsNeedYou = duels?.needYou,
        ).takeIf { navV2 },
        onOpenHistory = { navigator.openOwner(HistoryRootKey) }.takeIf { navV2 },
    )
    historyEntry(navigator, historyLayout)
    playEntry(
        navigator,
        playLayout,
        onOpenHistory = { navigator.pushOnce(HistoryKey()) },
        codeDraft = codeDraft,
        libraryRequest = { libraryRequests.pending },
        onLibraryRequestTaken = libraryRequests::take,
        onCodeDraftTaken = onCodeDraftTaken,
        duels = duels,
        onOpenDuel = onOpenDuel,
        showGameBoard = navV2,
        onOpenGameHistory = { navigator.openOwner(HistoryRootKey) }.takeIf { navV2 },
        signInInPlace = navV2,
    )
    resultEntry(navigator, onOpenBoard = { game: GameId -> libraryRequests.openGamePage(navigator, game.value) }.takeIf { navV2 })
    createEntry(navigator)
    editorEntry(navigator, playLayout)
    publishedPuzzleEntry(navigator, playLayout)
    val back: () -> Unit = { navigator.pop() }
    profileEntry(
        onOpenHistory = { game -> navigator.pushOnce(HistoryKey(game.value)) },
        onOpenCatalog = { navigator.push(CatalogKey) },
        onBack = back,
        slim = navV2,
        onProfileBack = back.takeIf { navV2 },
    )
    competeEntry(navigator, playLayout)
    entry<CatalogKey> { CatalogScreen(openGame = { navigator.pushOnce(GameKey(it)) }) }
}
