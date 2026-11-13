package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.app.CatalogScreen
import com.ivandsky.puzzlebox.app.DebugSettingsSlot
import com.ivandsky.puzzlebox.compete.navigation.competeEntry
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.create.navigation.publishedPuzzleEntry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.history.navigation.historyEntry
import com.ivandsky.puzzlebox.history.navigation.ranksEntry
import com.ivandsky.puzzlebox.home.HomeDuelsSummary
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.home.HomeSummaries
import com.ivandsky.puzzlebox.home.navigation.homeEntry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.navigation.playEntry
import com.ivandsky.puzzlebox.play.navigation.resultEntry
import com.ivandsky.puzzlebox.profile.api.LinkedInLoginKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import com.ivandsky.puzzlebox.profile.navigation.profileEntry
import com.ivandsky.puzzlebox.profile.settings.VisibilitySlot
import java.time.LocalDate

/**
 * The app's route table: one entry per [NavKey], every destination real —
 * each feature contributes its own entries; the shell owns only the catalog page.
 * Building it is a plain lookup construction — no composition — so NavRoutesTest
 * resolves each key against it and fails fast if a pushed key ever lacks a route.
 *
 * Loop wiring: Home's Today card selects the Play tab; its history teaser pushes History
 * on Home's stack, and the games hub's "Older puzzles" pushes it on Play's stack; history
 * rows push the day's game key; Settings' Component catalog row pushes [CatalogKey] on the current
 * tab's stack.
 * The Create tab's New puzzle and its draft rows push that draft's
 * Studio on Create's stack, and its published rows that puzzle's page. Back pops the current tab's stack ([Navigator.pop]),
 * including after process-death restore. A [codeDraft] (a tapped link's text that isn't a
 * code) opens the Play library's code sheet holding it.
 *
 * Each content type has one owner (ADR-0027): Today's rank and duels summaries and the game
 * page's "Your history" row open their owner (a game page or
 * Games section through [libraryRequests], History's tab root through [openOwner]); Today
 * hides what is owned elsewhere, the game page draws its board, the tablet times card signs
 * in in place, and Profile has its own back and a gear to Settings.
 *
 * [linkedIn] turns on the LinkedIn entry points (the game page's reconnect and Profile's
 * connect), both opening [LinkedInLoginKey]; off, those entries stay hidden.
 *
 * [ranks] (the LeaderboardHub flag, which only the app reads) makes [HistoryRootKey] the Ranks
 * hub and a pushed [HistoryKey] opens it at that game and day; off, they are History's root and browser.
 * With it on, Today's rank row, the game page's board summary row and Result's rank chip all push that [HistoryKey] ([openRanks]); off,
 * Today's rank row opens the game page and the page and Result keep their board and Today chip.
 * [visibility] (null with the flag off) adds Settings' Visibility section.
 */
internal fun puzzleBoxEntryProvider(
    navigator: Navigator,
    homeLayout: HomeLayout,
    playLayout: PlayLayout,
    historyLayout: HistoryLayout,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
    libraryRequests: LibraryRequests = LibraryRequests(),
    linkedIn: Boolean = false,
    ranks: Boolean = false,
    visibility: VisibilitySlot? = null,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    homeEntry(
        navigator,
        homeLayout,
        title = topLevelDestinations.first { it.key == HomeKey }.label,
        summaries = HomeSummaries(
            onOpenRank = { game ->
                if (ranks) openRanks(navigator)(game, null) else libraryRequests.openGamePage(navigator, game.value)
            },
            duels = duels?.let {
                HomeDuelsSummary(
                    onOpen = { libraryRequests.openGamesSection(navigator, GamesTab.Duels) },
                    needYou = it.needYou,
                )
            },
        ),
        onOpenHistory = { navigator.openOwner(HistoryRootKey) },
    )
    if (ranks) {
        ranksEntry(navigator, historyLayout, linkedIn)
    } else {
        historyEntry(navigator, historyLayout)
    }
    playEntry(
        navigator,
        playLayout,
        codeDraft = codeDraft,
        libraryRequest = { libraryRequests.pending },
        onLibraryRequestTaken = libraryRequests::take,
        onCodeDraftTaken = onCodeDraftTaken,
        duels = duels,
        onOpenGameHistory = openRanks(navigator),
        onReconnectLinkedIn = { navigator.pushOnce(LinkedInLoginKey) }.takeIf { linkedIn },
        onOpenRanks = openRanks(navigator).takeIf { ranks },
    )
    resultEntry(navigator, linkedIn = linkedIn, onOpenRanks = openRanks(navigator).takeIf { ranks })
    createEntry(navigator)
    editorEntry(navigator, playLayout)
    publishedPuzzleEntry(navigator, playLayout)
    val back: () -> Unit = { navigator.pop() }
    profileEntry(
        onOpenCatalog = { navigator.push(CatalogKey) },
        onBack = back,
        onOpenSettings = { navigator.pushOnce(SettingsKey) },
        onOpenLinkedInLogin = { navigator.pushOnce(LinkedInLoginKey) }.takeIf { linkedIn },
        debugSections = { DebugSettingsSlot(openGame = { navigator.pushOnce(GameKey(it)) }) },
        visibility = visibility,
    )
    competeEntry(navigator, playLayout)
    entry<CatalogKey> { CatalogScreen() }
}

/**
 * Opens [HistoryKey] for a game and day (null: today) on the current stack, once per tap. With [puzzleBoxEntryProvider]'s
 * `ranks` on that key is the Ranks hub at that board; off, it is History's browser at that game and day.
 */
internal fun openRanks(navigator: Navigator): (GameId, LocalDate?) -> Unit =
    { game, day -> navigator.pushOnce(HistoryKey(game.value, day?.toString())) }
