package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.app.CatalogScreen
import com.ivandsky.puzzlebox.app.DebugSettingsSlot
import com.ivandsky.puzzlebox.compete.navigation.competeEntry
import com.ivandsky.puzzlebox.create.TestSolvePlaySlot
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.create.navigation.publishedPuzzleEntry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.history.navigation.ranksEntry
import com.ivandsky.puzzlebox.home.HomeDuelsSummary
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.home.HomeSummaries
import com.ivandsky.puzzlebox.home.navigation.homeEntry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.GamesTab
import com.ivandsky.puzzlebox.play.SandboxPlay
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.navigation.communityEntry
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
 * Loop wiring: Home's Today card selects the Play tab; its history teaser opens
 * the Ranks tab; the Ranks hub's rows push the day's game key; Settings' Component catalog row pushes [CatalogKey] on the current
 * tab's stack.
 * The Create tab's New puzzle and its draft rows push that draft's
 * Studio on Create's stack, and its published rows that puzzle's page. Once Custom lives in Games, [custom] fills
 * the Games Custom pill (its rows push the same two screens on the Games stack) and [makeOne] backs the game
 * page's Make one (the default size); both are null until then, and neither door shows. Back pops the layer or
 * the current tab's stack
 * ([Navigator.pop]), including after process-death restore. A [codeDraft] (a tapped link's text that isn't a
 * code) opens the Play library's code sheet holding it. With a Community tab in [tabSet] (M10-1) Custom is that tab's
 * root ([communityEntry]) and not a Games pill: [custom] fills it, with its own code sheet, and Games shows Dailies and Duels.
 *
 * Each content type has one owner (ADR-0027): Today's rank summary opens the Ranks hub ([openRanks]) and its
 * duels summary the Games section through [libraryRequests]. Today hides what is owned elsewhere; the game page
 * shows its rank row, the tablet times card signs in in place, and Profile has its own back and a gear to Settings.
 *
 * The LinkedIn entry points (the game page's reconnect and Profile's connect) both open [LinkedInLoginKey].
 *
 * [HistoryRootKey] is the Ranks hub and a pushed [HistoryKey] opens it at that game and day. Today's rank row,
 * the game page's rank summary row and Result's rank chip all push that [HistoryKey] ([openRanks]).
 * [visibility] backs Settings' Visibility section. The Studio's test solve can open on the play screen ([TestSolveOnPlay]).
 *
 * Today's day and streak summaries open the hub ([homeSummaries]), and the hub takes [tuneDefaults] for its Tune sheet.
 * Profile's own pages (Settings, the LinkedIn login, the catalog) stack on Profile inside the layer above the tabs
 * ([Navigator.pushLayer]).
 */
internal fun puzzleBoxEntryProvider(
    navigator: Navigator,
    tabSet: TabSet,
    homeLayout: HomeLayout,
    playLayout: PlayLayout,
    historyLayout: HistoryLayout,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    duels: DuelsSlot? = null,
    custom: CustomSlot? = null,
    makeOne: MakeOne? = null,
    libraryRequests: LibraryRequests = LibraryRequests(),
    underlineTabs: Boolean = false,
    visibility: VisibilitySlot,
    tuneDefaults: RanksTuneDefaults,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    homeEntry(
        navigator,
        homeLayout,
        title = topLevelDestinations.first { it.key == HomeKey }.label,
        summaries = homeSummaries(navigator, tabSet, duels, libraryRequests),
    )
    ranksEntry(navigator, historyLayout, linkedIn = true, tuneDefaults = tuneDefaults)
    val communityTab = tabSet.hasCommunityTab
    playEntry(
        navigator,
        playLayout,
        codeDraft = codeDraft,
        libraryRequest = { libraryRequests.pending },
        onLibraryRequestTaken = libraryRequests::take,
        onCodeDraftTaken = onCodeDraftTaken,
        duels = duels,
        // With a Community tab the Custom list is that tab's, not a Games pill.
        custom = if (communityTab) null else custom,
        onReconnectLinkedIn = { navigator.pushOnce(LinkedInLoginKey) },
        onOpenRanks = openRanks(navigator, tabSet),
        onMakeOne = makeOne?.let { make -> { game -> make(game) } },
        communityTab = communityTab,
        underlineTabs = underlineTabs,
    )
    communityEntry(
        navigator,
        playLayout,
        custom = custom,
        codeDraft = codeDraft,
        onCodeDraftTaken = onCodeDraftTaken,
        duels = duels,
    )
    resultEntry(
        navigator,
        linkedIn = true,
        onOpenRanks = openRanks(navigator, tabSet),
        onChallenge = { prefill -> libraryRequests.openChallenge(navigator, tabSet, prefill) },
    )
    createEntry(navigator)
    editorEntry(navigator, playLayout, testSolvePlay = TestSolveOnPlay)
    publishedPuzzleEntry(navigator, playLayout)
    val back: () -> Unit = { navigator.pop() }
    profileEntry(
        onOpenCatalog = { navigator.pushLayer(CatalogKey) },
        onBack = back,
        onOpenSettings = { navigator.pushLayer(SettingsKey) },
        onOpenLinkedInLogin = { navigator.pushLayer(LinkedInLoginKey) },
        debugSections = { DebugSettingsSlot(openGame = { navigator.pushOnce(GameKey(it)) }) },
        visibility = visibility,
    )
    competeEntry(navigator, playLayout)
    entry<CatalogKey> { CatalogScreen() }
}

/**
 * The Studio's test solve on the play feature's regular play screen: create and play share no edge (ADR-0008), so
 * the shell hands one the other's screen. Whether a test solve uses it is the Studio view model's call.
 */
private val TestSolveOnPlay = TestSolvePlaySlot { ui, controller, title, label, layout, onBack, onSolved, modifier ->
    SandboxPlay(ui, controller, title, label, layout, onBack, onSolved, modifier)
}

/**
 * Opens [HistoryKey] for a game and day (null: today) on the Ranks tab, once per tap: the hub at that board. One back
 * returns to the screen the link was on.
 */
internal fun openRanks(navigator: Navigator, tabSet: TabSet): (GameId, LocalDate?) -> Unit =
    { game, day -> navigator.openOnOwnerTab(HistoryKey(game.value, day?.toString()), tabSet) }

/**
 * What Today opens: the rank row the hub at that game, the duels row the Games section (its owner under [tabSet]),
 * the day summary the hub on Overall at that ISO day and the streak summary the hub with the calendar expanded.
 */
internal fun homeSummaries(
    navigator: Navigator,
    tabSet: TabSet,
    duels: DuelsSlot?,
    libraryRequests: LibraryRequests,
): HomeSummaries = HomeSummaries(
    onOpenRank = { game -> openRanks(navigator, tabSet)(game, null) },
    duels = duels?.let {
        HomeDuelsSummary(
            onOpen = { libraryRequests.openGamesSection(navigator, tabSet, GamesTab.Duels) },
            needYou = it.needYou,
        )
    },
    onOpenDay = { day: String -> navigator.openOnOwnerTab(HistoryKey(gameId = null, day = day), tabSet) },
    onOpenStreak = { navigator.openOnOwnerTab(HistoryKey(calendarExpanded = true), tabSet) },
)
