package com.ivandsky.puzzlebox.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.app.nav.Catalog
import com.ivandsky.puzzlebox.app.nav.History
import com.ivandsky.puzzlebox.app.nav.Home
import com.ivandsky.puzzlebox.app.nav.Play
import com.ivandsky.puzzlebox.app.nav.Profile
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.screens.ProfileScreen
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.TokenCatalogScreen
import com.ivandsky.puzzlebox.gameui.playLayoutFor
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.HistoryRoute
import com.ivandsky.puzzlebox.history.historyLayoutFor
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.HomeRoute
import com.ivandsky.puzzlebox.home.homeLayoutFor
import com.ivandsky.puzzlebox.play.PlayRoute

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
                transitionSpec = {
                    screenEnterTransform() togetherWith screenExitTransform()
                },
                popTransitionSpec = {
                    screenEnterTransform() togetherWith screenExitTransform()
                },
                entryProvider = entryProvider {
                    entry<Home> {
                        SceneCanvas {
                            HomeRoute(
                                layout = layout,
                                themeMode = themeMode,
                                onSetThemeMode = onSetThemeMode,
                                onOpenHistory = { backStack.add(History) },
                            )
                        }
                    }
                    entry<History> {
                        SceneCanvas {
                            HistoryRoute(
                                layout = historyLayout,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    }
                    entry<Play> {
                        SceneCanvas {
                            PlayRoute(
                                layout = playLayout,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    }
                    entry<Profile> {
                        SceneCanvas { ProfileScreen(onOpenCatalog = { backStack.add(Catalog) }) }
                    }
                    entry<Catalog> {
                        SceneCanvas { CatalogScreenWithShowkaseTrial() }
                    }
                },
            )
        }
    }
}

// Tab selection resets to that tab's root — no cross-tab back stacking.
private fun select(key: NavKey, backStack: NavBackStack<NavKey>) {
    backStack.clear()
    backStack.add(key)
}

// Ticket design-previews-08: the "Showkase" button opens the custom flat dark browser
// from :app's debug source set — still launched by class name so :app main keeps no
// compile-time reference to debug-only code, and the FLAG_DEBUGGABLE gate keeps the
// button out of release. The stock AAR activity stays merged but is no longer the
// target; it needs no intent extras from here (that requirement was stock-activity-only).
private const val SHOWKASE_BROWSER_ACTIVITY =
    "com.ivandsky.puzzlebox.app.debug.ShowkaseBrowserActivity"

@Composable
private fun CatalogScreenWithShowkaseTrial() {
    val context = LocalContext.current
    val isDebuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    Box(Modifier.fillMaxSize()) {
        TokenCatalogScreen()
        if (isDebuggable) {
            Button(
                onClick = {
                    context.startActivity(
                        Intent().setClassName(context.packageName, SHOWKASE_BROWSER_ACTIVITY),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Text("Showkase")
            }
        }
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

// Opaque canvas behind every nav scene: without it the AnimatedContent layers blend and
// screens read as ghosts of each other during the transition.
@Composable
private fun SceneCanvas(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}
