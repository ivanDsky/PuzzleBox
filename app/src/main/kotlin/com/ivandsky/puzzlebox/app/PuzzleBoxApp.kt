package com.ivandsky.puzzlebox.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.app.nav.FriendLink
import com.ivandsky.puzzlebox.app.nav.LocalChromePadding
import com.ivandsky.puzzlebox.app.nav.PuzzleBoxNavShell
import com.ivandsky.puzzlebox.app.nav.TopLevelNavBar
import com.ivandsky.puzzlebox.app.nav.TopLevelNavRail
import com.ivandsky.puzzlebox.app.nav.applyFriendLink
import com.ivandsky.puzzlebox.app.nav.applySharedText
import com.ivandsky.puzzlebox.app.nav.isImmersive
import com.ivandsky.puzzlebox.app.nav.playTabBadged
import com.ivandsky.puzzlebox.app.nav.rememberNavigator
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.Competitions
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.tab.DuelsTab
import com.ivandsky.puzzlebox.compete.tab.rememberDuelsNeedYou
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.playLayoutFor
import com.ivandsky.puzzlebox.history.historyLayoutFor
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.homeLayoutFor
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.api.PlayKey
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * The app shell: tab bar (or rail) beneath [PuzzleBoxNavShell]. The highlight is the
 * [navigator]'s selected tab, so it always names the tab that owns the screen on top.
 * The Play tab carries a dot while any of today's games is in progress (A13, SC11).
 * Play is immersive (A15, N2): while the selected tab's top is immersive ([isImmersive]: a
 * board or a result, a duel's leg board or result, or a duel's reveal) neither bar nor rail
 * shows, and back is the way out.
 *
 * Full-screen motion (polish 04): the chrome sits UNDER the scenes, which leave its strip
 * unpainted ([LocalChromePadding]), so a board covers it as one window and the chrome leaves
 * animated with the page beneath it, never in a one-frame cut: the bar parallaxes with the
 * outgoing page (phone), the rail fades and shifts out early in the lift (tablet).
 *
 * A [pendingLink] (a tapped share link's URI) lands on the back stack in the composition
 * that first sees it, so a cold start draws the friend's card on its first frame; then
 * [onLinkApplied]. A link whose code doesn't parse opens Play's code sheet holding its text.
 * A [pendingShare] (shared text waiting in the store, which `MainActivity` filled) shows
 * Create's root the same way, then [onShareApplied]; the root imports the text.
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
    todaySet: ObserveTodaySet = koinInject(),
) {
    var codeDraft by rememberSaveable { mutableStateOf<String?>(null) }
    if (pendingLink != null) {
        remember(pendingLink) {
            FriendLink.of(pendingLink)?.let { link ->
                navigator.applyFriendLink(link)
                if (link is FriendLink.Invalid) codeDraft = link.text
            }
        }
        SideEffect(onLinkApplied)
    }
    if (pendingShare) {
        remember { navigator.applySharedText() }
        SideEffect(onShareApplied)
    }
    // Two-pane needs width AND height: a large tablet in portrait still measures
    // Expanded by width class, so the orientation comes from the current window
    // container size. One policy call drives both the nav shape and Home's pane
    // arrangement, and containerSize keeps a live rotation recomposing without a restart.
    val isLandscape = with(LocalWindowInfo.current) {
        containerSize.width > containerSize.height
    }
    val layout = homeLayoutFor(
        widthSizeClass = windowWidthSizeClass,
        isLandscape = isLandscape,
    )
    // Same 07 policy as home, play's own pane shapes (game-ui): two-pane iff expanded
    // landscape; portrait of any width stays single-pane.
    val playLayout = playLayoutFor(
        widthSizeClass = windowWidthSizeClass,
        isLandscape = isLandscape,
    )
    // Same 07 policy for history's list-detail panes.
    val historyLayout = historyLayoutFor(
        widthSizeClass = windowWidthSizeClass,
        isLandscape = isLandscape,
    )
    val expanded = layout == HomeLayout.TwoPane
    val selected = navigator.state.tab
    val top = navigator.state.currentStack.last()
    val immersive = isImmersive(top)
    val anyInProgress by remember(todaySet) {
        todaySet().map { entries -> entries.any { it.status is TodayStatus.InProgress } }
    }.collectAsStateWithLifecycle(initialValue = false)
    val competitions = Competitions in koinInject<ReleaseFlags>().enabled.collectAsStateWithLifecycle().value
    val duels = if (competitions) {
        remember(navigator) {
            DuelsSlot(
                content = { DuelsTab(onOpenDuel = navigator::pushOnce, onOpenReveal = navigator::pushOnce) },
                needYou = { rememberDuelsNeedYou() },
            )
        }
    } else {
        null
    }
    val onOpenDuel: ((DuelKey) -> Unit)? = if (competitions) {
        { navigator.pushOnce(it) }
    } else {
        null
    }
    val badged = playTabBadged(anyInProgress, duelsNeedYou = duels?.needYou?.invoke() ?: 0)

    val reduceMotion = rememberReducedMotion()
    val previousTab = remember { TabRef(selected) }
    val tabChanged = previousTab.tab != selected
    SideEffect { previousTab.tab = selected }
    val chrome = chromeMotion(expanded, tabChanged, reduceMotion, LocalLayoutDirection.current)
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    var railWidth by remember { mutableStateOf(0.dp) }

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
                    onSelect = navigator::selectTab,
                    badged = badged,
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
                    onSelect = navigator::selectTab,
                    badged = badged,
                    modifier = Modifier
                        .fillMaxHeight()
                        .onSizeChanged { railWidth = with(density) { it.width.toDp() } },
                )
            }
            CompositionLocalProvider(LocalChromePadding provides chromePadding(expanded, barHeight, railWidth)) {
                PuzzleBoxNavShell(
                    navigator = navigator,
                    homeLayout = layout,
                    playLayout = playLayout,
                    historyLayout = historyLayout,
                    modifier = Modifier.fillMaxSize(),
                    codeDraft = { codeDraft },
                    onCodeDraftTaken = { codeDraft = null },
                    duels = duels,
                    onOpenDuel = onOpenDuel,
                )
            }
        }
    }
}

/** The tab the last composition showed; read to tell a tab switch from a push. */
private class TabRef(var tab: Any)

private class ChromeMotion(val enter: EnterTransition, val exit: ExitTransition)

/**
 * How the bar or rail comes and goes. Phone: it rides the page beneath, parallaxing toward
 * the start on the slide clocks. Tablet: the rail fades and shifts out over the lift's first
 * stretch and back late in its return. A tab switch (onto a tab already in a game) fades it
 * on the tab clock; reduced motion is the short fade.
 */
@Composable
private fun chromeMotion(
    expanded: Boolean,
    tabChanged: Boolean,
    reduceMotion: Boolean,
    layoutDirection: LayoutDirection,
): ChromeMotion {
    val startward = if (layoutDirection == LayoutDirection.Ltr) -1 else 1
    val shiftPx = with(LocalDensity.current) { MotionTokens.LiftNavShift.roundToPx() }
    return when {
        reduceMotion -> ChromeMotion(
            fadeIn(tween(MotionTokens.ReducedFadeDuration)),
            fadeOut(tween(MotionTokens.ReducedFadeDuration)),
        )
        tabChanged -> ChromeMotion(
            fadeIn(tween(MotionTokens.NavTabEnter, MotionTokens.NavTabEnterDelay, MotionTokens.EasingStandard)),
            fadeOut(tween(MotionTokens.NavTabExit, easing = MotionTokens.EasingLinear)),
        )
        expanded -> {
            val outMillis = (MotionTokens.LiftEnter * MotionTokens.LiftNavFraction).roundToInt()
            val inDelay = (MotionTokens.LiftExit * MotionTokens.LiftNavFraction).roundToInt()
            val inMillis = MotionTokens.LiftExit - inDelay
            ChromeMotion(
                fadeIn(tween(inMillis, inDelay, MotionTokens.EasingStandard)) +
                    slideInHorizontally(tween(inMillis, inDelay, MotionTokens.EasingStandard)) { startward * shiftPx },
                fadeOut(tween(outMillis, easing = MotionTokens.EasingStandard)) +
                    slideOutHorizontally(tween(outMillis, easing = MotionTokens.EasingStandard)) { startward * shiftPx },
            )
        }
        else -> {
            val parallax = { width: Int -> startward * (width * MotionTokens.NavParallaxFraction).roundToInt() }
            ChromeMotion(
                slideInHorizontally(tween(MotionTokens.NavFullSlidePop, easing = MotionTokens.EasingStandard), parallax),
                slideOutHorizontally(
                    tween(MotionTokens.NavFullSlideEnter, easing = MotionTokens.EasingEmphasizedDecelerate),
                    parallax,
                ),
            )
        }
    }
}

/** System bars everywhere, except the side the bar or rail holds, which is the chrome's own size. */
@Composable
private fun chromePadding(expanded: Boolean, barHeight: Dp, railWidth: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = if (expanded) railWidth else bars.calculateStartPadding(direction),
        top = bars.calculateTopPadding(),
        end = bars.calculateEndPadding(direction),
        bottom = if (expanded) bars.calculateBottomPadding() else barHeight,
    )
}
