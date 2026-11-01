package com.ivandsky.puzzlebox.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.app.nav.Catalog
import com.ivandsky.puzzlebox.app.nav.History
import com.ivandsky.puzzlebox.app.nav.Home
import com.ivandsky.puzzlebox.app.nav.Play
import com.ivandsky.puzzlebox.app.nav.Profile
import com.ivandsky.puzzlebox.app.nav.ReplayZip
import com.ivandsky.puzzlebox.app.nav.Result
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.screens.CatalogUnavailableScreen
import com.ivandsky.puzzlebox.app.screens.ProfileRoute
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.playLayoutFor
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.HistoryRoute
import com.ivandsky.puzzlebox.history.historyLayoutFor
import com.ivandsky.puzzlebox.app.screens.CatalogUnavailableScreen
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.HomeRoute
import com.ivandsky.puzzlebox.home.homeLayoutFor
import com.ivandsky.puzzlebox.play.PlayRoute
import com.ivandsky.puzzlebox.play.ResultRoute
import java.util.Base64

@Composable
fun PuzzleBoxApp(
    windowWidthSizeClass: WindowWidthSizeClass,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val backStack = rememberNavBackStack(Home)
    // Accessibility (spec A3): animator scale 0 is the system's reduced-motion signal —
    // the result fade-through collapses to opacity-only.
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Two-pane moves navigation to a rail; the bottom bar is every other shape.
            if (!expanded) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = backStack.lastOrNull() == destination.key
                        NavigationBarItem(
                            selected = selected,
                            onClick = { select(destination.key, backStack) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (expanded) {
                NavigationRail {
                    topLevelDestinations.forEach { destination ->
                        val selected = backStack.lastOrNull() == destination.key
                        NavigationRailItem(
                            selected = selected,
                            onClick = { select(destination.key, backStack) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.weight(1f),
                // Fade-through: the outgoing screen fades out in 90ms BEFORE the incoming
                // one starts, so the two contents never superimpose mid-flight (Nav3's
                // simultaneous crossfade showed both screens' text over each other).
                // Slide is a residual 1/24 — tab switches aren't hierarchical.
                // Ticket 23: entering the RESULT route is the run's only new animation —
                // opacity 0→1 + scale 0.92→1 on one critically damped ~0.3s spring
                // (spec A3); reduced motion keeps the opacity half only.
                transitionSpec = {
                    if (targetState.key == Result) {
                        resultEnterTransform(reduceMotion) togetherWith screenExitTransform()
                    } else {
                        screenEnterTransform() togetherWith screenExitTransform()
                    }
                },
                popTransitionSpec = {
                    screenEnterTransform() togetherWith screenExitTransform()
                },
                entryProvider = puzzleBoxEntryProvider(
                    backStack = backStack,
                    homeLayout = layout,
                    playLayout = playLayout,
                    historyLayout = historyLayout,
                    themeMode = themeMode,
                    onSetThemeMode = onSetThemeMode,
                ),
            )
        }
    }
}

/**
 * The app's route table (ticket 17): one entry per [NavKey], every destination real.
 * Building it is a plain lookup construction — no composition — so [NavRoutesTest]
 * resolves each key against it and fails fast if a pushed key ever lacks a route.
 *
 * Loop wiring: Home's Today card pushes [Play] (today's board is fetched fresh by the
 * play route); its history teaser pushes [History]; history rows push [ReplayZip] with
 * the day's session blob; the account card opens the [Profile] account surface as a tab
 * root; Profile's catalog link pushes [Catalog] as a deliberate deep page — NavDisplay's
 * default back pop returns it to Profile, including after process-death restore.
 */
internal fun puzzleBoxEntryProvider(
    backStack: NavBackStack<NavKey>,
    homeLayout: HomeLayout,
    playLayout: PlayLayout,
    historyLayout: HistoryLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<Home> {
        SceneCanvas {
            HomeRoute(
                layout = homeLayout,
                themeMode = themeMode,
                onSetThemeMode = onSetThemeMode,
                onPlayToday = { backStack.pushOnce(Play) },
                onOpenProfile = { select(Profile, backStack) },
                onOpenHistory = { backStack.pushOnce(History) },
            )
        }
    }
    entry<History> {
        SceneCanvas {
            HistoryRoute(
                layout = historyLayout,
                onBack = { backStack.removeLastOrNull() },
                onReplay = { ready ->
                    backStack.add(
                        ReplayZip(Base64.getEncoder().encodeToString(ready.session)),
                    )                },
            )
        }
    }
    entry<ReplayZip> { key ->
        SceneCanvas {
            PlayRoute(
                layout = playLayout,
                onBack = { backStack.removeLastOrNull() },
                onSolved = { timeMs, hintsUsed -> backStack.solveToResult(Result(timeMs, hintsUsed)) },
                restoredSession = runCatching {
                    Base64.getDecoder().decode(key.session)
                }.getOrNull(),
            )
        }
    }
    entry<Play> {
        SceneCanvas {
            PlayRoute(
                layout = playLayout,
                onBack = { backStack.removeLastOrNull() },
                onSolved = { timeMs, hintsUsed -> backStack.solveToResult(Result(timeMs, hintsUsed)) },
            )
        }
    }
    entry<Result> { key ->
        SceneCanvas {
            ResultRoute(
                timeMs = key.timeMs,
                hintsUsed = key.hintsUsed,
                onPlayAgain = { backStack.playAgainFromResult() },
            )
        }
    }
    entry<Profile> {
        SceneCanvas { ProfileRoute(onOpenCatalog = { backStack.add(Catalog) }) }
    }
    entry<Catalog> {
        SceneCanvas { CatalogScreen() }
    }
}

// Tab selection resets to that tab's root — no cross-tab back stacking.
private fun select(key: NavKey, backStack: NavBackStack<NavKey>) {
    backStack.clear()
    backStack.add(key)
}

// Catalog route (ticket showkase-v2-01): debug builds render the new Showkase browser
// inline — it follows PuzzleBoxTheme instead of the old pinned-dark activity. Main cannot
// reference debug-only code at compile time (the debug source set doesn't exist in
// release), so the browser is registered reflectively: loading CatalogDebugBinding runs
// its class-init, which assigns DebugCatalogContent. The FLAG_DEBUGGABLE gate keeps
// release from ever touching the reflection path; anything that can't or shouldn't load
// the browser (release, non-debuggable) gets the cutover placeholder (showkase-v2-05) —
// the old screens are gone, not demoted to a fallback.
internal object DebugCatalogContent {
    @Volatile
    var browser: (
@Composable () -> Unit)? = null
}

private const val DEBUG_CATALOG_BINDING =
    "com.ivandsky.puzzlebox.app.debug.CatalogDebugBinding"
@Composable
private fun CatalogScreen() {
    val debugBrowser = debugCatalogBrowser()
    if (debugBrowser != null) {
        debugBrowser()
    } else {
        CatalogUnavailableScreen()
    }
}

@Composable
private fun debugCatalogBrowser(): (
@Composable () -> Unit)? {
    val isDebuggable = LocalContext.current.applicationInfo.flags and
        android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    return if (isDebuggable) {
        remember {
            try {
                // Class.forName initializes the class, running the binding's registration.
                Class.forName(DEBUG_CATALOG_BINDING)
                DebugCatalogContent.browser
            } catch (_: Throwable) {
                null
            }
        }
    } else {
        null
    }
}

// Fade-through screen transition: 90ms accelerated fade-out, then the incoming screen
// fades in over 200ms (delayed 90ms) with a residual 1/24 slide. Sequencing guarantees
// only one screen's content is ever visible; every scene root paints an opaque canvas
// (SceneCanvas) so nothing underneath can bleed through. Tokens keep one vocabulary.
private fun AnimatedContentTransitionScope<*>.screenEnterTransform() =
    fadeIn(
        tween(MotionTokens.NavTransitionEnter, delayMillis = MotionTokens.NavTransitionEnterDelay, easing = MotionTokens.EasingEmphasizedDecelerate),
    ) + slideInHorizontally(
        tween(MotionTokens.NavTransitionEnter, delayMillis = MotionTokens.NavTransitionEnterDelay, easing = MotionTokens.EasingEmphasizedDecelerate),
    ) { it / 24 }

private fun AnimatedContentTransitionScope<*>.screenExitTransform() =
    fadeOut(tween(MotionTokens.NavTransitionExit, easing = MotionTokens.EasingEmphasizedAccelerate))

// Ticket 23 — the run's ONLY new animation (spec A3): the result screen enters with a
// fade-through — opacity 0→1 + scale 0.92→1 on ONE critically damped spring (~0.3s
// response, MotionTokens.SpringStiffnessDefault). Reduced motion (animator scale 0)
// keeps the opacity half only. The outgoing screen still fades out in its own 90ms, so
// the arrival reads as a fade-through, not a crossfade.
private fun resultEnterTransform(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        fadeIn(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedDecelerate))
    } else {
        val enter = spring<Float>(
            dampingRatio = MotionTokens.SpringDampingDefault,
            stiffness = MotionTokens.SpringStiffnessDefault,
        )
        fadeIn(enter) + scaleIn(initialScale = 0.92f, animationSpec = enter)
    }

// Opaque canvas behind every nav scene: without it the AnimatedContent layers blend and
// screens read as ghosts of each other during the transition.
@Composable
private fun SceneCanvas(content: 
@Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}
