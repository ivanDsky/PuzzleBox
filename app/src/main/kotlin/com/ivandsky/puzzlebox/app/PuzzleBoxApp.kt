package com.ivandsky.puzzlebox.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.nav.FriendLink
import com.ivandsky.puzzlebox.app.nav.LocalChromePadding
import com.ivandsky.puzzlebox.app.nav.MakeOne
import com.ivandsky.puzzlebox.app.nav.PuzzleBoxNavShell
import com.ivandsky.puzzlebox.app.nav.ShellAvatar
import com.ivandsky.puzzlebox.app.nav.ShellAvatarSize
import com.ivandsky.puzzlebox.app.nav.TabRef
import com.ivandsky.puzzlebox.app.nav.TopLevelNavBar
import com.ivandsky.puzzlebox.app.nav.TopLevelNavRail
import com.ivandsky.puzzlebox.app.nav.applyFriendLink
import com.ivandsky.puzzlebox.app.nav.applySharedText
import com.ivandsky.puzzlebox.app.nav.applyShortcutLanding
import com.ivandsky.puzzlebox.app.nav.chromeMotion
import com.ivandsky.puzzlebox.app.nav.chromePadding
import com.ivandsky.puzzlebox.app.nav.isImmersive
import com.ivandsky.puzzlebox.app.nav.isPlayable
import com.ivandsky.puzzlebox.app.nav.playTabBadged
import com.ivandsky.puzzlebox.app.nav.rememberLibraryRequests
import com.ivandsky.puzzlebox.app.nav.rememberNavigator
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.shortcut.ResolveShortcutLanding
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.PanePolicy
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.FirstScreenReady
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * The app shell: tab bar (or rail) beneath [PuzzleBoxNavShell]. The tabs are [topLevelDestinations], which the
 * default [navigator] starts from too. The highlight is the
 * [navigator]'s selected tab, so it always names the tab that owns the screen on top, or the
 * tab under the Profile layer while that is open.
 * The Play tab carries a dot while any of today's games is in progress.
 * The shell hands Community its Custom body ([rememberCustomSlot]), Games its Train tab ([rememberTrainSlot], none
 * while no game has a trainer) and the Make-one doors their action ([MakeOne]).
 * Play is immersive: while the screen on top is immersive ([isImmersive]: a
 * board or a result, a duel's leg board or result, or a duel's reveal) neither bar nor rail
 * shows, and back is the way out.
 *
 * Full-screen motion (polish 04): the chrome sits UNDER the scenes, which leave its strip
 * unpainted ([LocalChromePadding]), so a board covers it as one window and the chrome leaves
 * animated with the page beneath it, never in a one-frame cut: the bar parallaxes with the
 * outgoing page (phone), the rail fades and shifts out early in the lift (tablet).
 *
 * The first composition's landing screen (after links, shared text and restore) that is not Home
 * marks [firstScreenReady] at once; Home's own ViewModel marks it for a Home landing.
 *
 * [windowWidthSizeClass] is no longer read: panes follow the container size via `PanePolicy`
 * (kept only so `MainActivity` stays as it is).
 *
 * A [pendingLink] (a tapped share link's URI) lands on the back stack in the composition
 * that first sees it, so a cold start draws the friend's card on its first frame; then
 * [onLinkApplied]. A link whose code doesn't parse opens Community's code sheet holding its text.
 * A duel link (`/m/`) opens the duel screen. A daily link (`/p/<game>/<day>`) opens that day's board on the Games tab;
 * one naming a game this build lacks or a day with no puzzle yet is dropped, the app opening as usual.
 * A [pendingShare] (shared text waiting in the store, which `MainActivity` filled) shows Community's root, the
 * list of drafts, the same way, then [onShareApplied]; that screen imports the text.
 * A [pendingShortcut] (a tapped launcher shortcut) lands once Today's first set is read, so Up next is the game the
 * Today card offers at the tap, then [onShortcutApplied]: a game's board with the Games list beneath it, or the Today
 * tab when nothing is left to play or the set will not load. A game this build lacks is dropped, the app opening as usual.
 */
@Composable
fun PuzzleBoxApp(
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    navigator: Navigator = rememberNavigator(),
    pendingLink: String? = null,
    onLinkApplied: () -> Unit = {},
    pendingShare: Boolean = false,
    onShareApplied: () -> Unit = {},
    pendingShortcut: ShortcutTarget? = null,
    onShortcutApplied: () -> Unit = {},
    todaySet: ObserveTodaySet = koinInject(),
    games: GameRegistry = koinInject(),
    calendar: GameCalendar = koinInject(),
    firstScreenReady: FirstScreenReady = koinInject(),
) {
    var codeDraft by rememberSaveable { mutableStateOf<String?>(null) }
    val libraryRequests = rememberLibraryRequests()
    if (pendingLink != null) {
        // Applied during composition so the first frame already shows the link's screen; the
        // result is the applied link (not Unit) so remember is a real keyed value, once per link.
        remember(pendingLink) {
            FriendLink.of(pendingLink)?.takeIf { it.isPlayable(games, calendar) }?.also { link ->
                navigator.applyFriendLink(link)
                if (link is FriendLink.Invalid) codeDraft = link.text
            }
        }
        SideEffect(onLinkApplied)
    }
    if (pendingShare) {
        remember { navigator.applySharedText(); true }
        SideEffect(onShareApplied)
    }
    if (pendingShortcut != null) {
        LaunchedEffect(pendingShortcut) {
            if (pendingShortcut !is ShortcutTarget.Game || games[pendingShortcut.id] != null) {
                val today = todaySet().catch { emit(emptyList()) }.firstOrNull().orEmpty()
                navigator.applyShortcutLanding(ResolveShortcutLanding()(pendingShortcut, today))
            }
            onShortcutApplied()
        }
    }
    // The single px→dp read site: one PanePolicy decision drives the nav shape and every
    // screen's pane arrangement. containerSize keeps a live rotation or resize recomposing.
    val twoPane = with(LocalDensity.current) {
        val size = LocalWindowInfo.current.containerSize
        PanePolicy.isTwoPane(size.width.toDp(), size.height.toDp())
    }
    val layout = if (twoPane) HomeLayout.TwoPane else HomeLayout.SinglePane
    val playLayout = if (twoPane) PlayLayout.TwoPane else PlayLayout.SinglePane
    val historyLayout = if (twoPane) HistoryLayout.TwoPane else HistoryLayout.SinglePane
    val expanded = layout == HomeLayout.TwoPane
    val identityService = koinInject<IdentityService>()
    val cloudSync = koinInject<CloudSync>()
    val destinations = topLevelDestinations
    val selected = navigator.state.tab
    // The screen on top: the Profile layer's when it is open, else the selected tab's.
    val top = navigator.state.visibleKeys.last()
    val immersive = isImmersive(top)
    // Home publishes its own readiness once its local state loads; any other landing screen is ready now.
    LaunchedEffect(Unit) { if (top !is HomeKey) firstScreenReady.markReady() }
    val anyInProgress by remember(todaySet) {
        todaySet().map { entries -> entries.any { it.status is TodayStatus.InProgress } }
    }.collectAsStateWithLifecycle(initialValue = false)
    val duels = rememberDuelsSlot(navigator, playLayout)
    val train = rememberTrainSlot(playLayout)
    val custom = rememberCustomSlot(navigator)
    val makeOne = rememberMakeOne(navigator)
    val badged = playTabBadged(anyInProgress, duelsNeedYou = duels.needYou())
    // A tap on the selected tab at its root is a reducer no-op, so the shell spots it here
    // and tells the roots; the tap still goes through selectTab. With the Profile layer open
    // the same tap only closes the layer, so the root is told nothing.
    val tabReselect = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val onSelect = { tab: NavKey ->
        val state = navigator.state
        val atRoot = tab == state.tab && state.currentStack.size == 1 && state.layer.isEmpty()
        navigator.selectTab(tab)
        if (atRoot) tabReselect.tryEmit(Unit)
    }

    val reduceMotion = rememberReducedMotion()
    val previousTab = remember { TabRef(selected) }
    val tabChanged = previousTab.tab != selected
    SideEffect { previousTab.tab = selected }
    val chrome = chromeMotion(expanded, tabChanged, reduceMotion, LocalLayoutDirection.current)
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    var railWidth by remember { mutableStateOf(0.dp) }

    VibrationGate {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                // Two-pane moves navigation to a rail; the bottom bar is every other shape.
                AnimatedVisibility(
                    visible = !expanded && !immersive,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = chrome.enter,
                    exit = chrome.exit,
                ) {
                    TopLevelNavBar(
                        selected = selected,
                        onSelect = onSelect,
                        badged = badged,
                        destinations = destinations,
                        modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    )
                }
                AnimatedVisibility(
                    visible = expanded && !immersive,
                    modifier = Modifier.align(Alignment.TopStart).fillMaxHeight(),
                    enter = chrome.enter,
                    exit = chrome.exit,
                ) {
                    TopLevelNavRail(
                        selected = selected,
                        onSelect = onSelect,
                        badged = badged,
                        destinations = destinations,
                        bottomActions = {
                            ShellAvatar(navigator, identityService.current, ShellAvatarSize.Rail)
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .onSizeChanged { railWidth = with(density) { it.width.toDp() } },
                    )
                }
                val contentPadding = chromePadding(expanded, barHeight, railWidth)
                CompositionLocalProvider(
                    LocalChromePadding provides contentPadding,
                    LocalTabReselect provides tabReselect,
                ) {
                    PuzzleBoxNavShell(
                        navigator = navigator,
                        homeLayout = layout,
                        playLayout = playLayout,
                        historyLayout = historyLayout,
                        modifier = Modifier.fillMaxSize(),
                        codeDraft = { codeDraft },
                        onCodeDraftTaken = { codeDraft = null },
                        duels = duels,
                        train = train,
                        custom = custom,
                        makeOne = makeOne,
                        libraryRequests = libraryRequests,
                    )
                }
                // The one-time "Welcome back" after an account's first restore (A33), above the chrome.
                RestoreWelcome(
                    restored = cloudSync.restored,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(contentPadding),
                )
            }
        }
    }
}

@Composable
private fun rememberMakeOne(navigator: Navigator): MakeOne {
    val createDraft = koinInject<CreateDraft>()
    val games = koinInject<GameRegistry>()
    val scope = rememberCoroutineScope()
    return remember(navigator, createDraft, games, scope) { MakeOne(createDraft, games, scope, navigator) }
}
