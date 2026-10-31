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
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.app.nav.Catalog
import com.ivandsky.puzzlebox.app.nav.Home
import com.ivandsky.puzzlebox.app.nav.Play
import com.ivandsky.puzzlebox.app.nav.Profile
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.screens.PlayScreen
import com.ivandsky.puzzlebox.app.screens.ProfileScreen
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.TokenCatalogScreen
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.HomeRoute
import com.ivandsky.puzzlebox.home.homeLayoutFor

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
                            )
                        }
                    }
                    entry<Play> { SceneCanvas { PlayScreen() } }
                    entry<Profile> {
                        SceneCanvas { ProfileScreen(onOpenCatalog = { backStack.add(Catalog) }) }
                    }
                    entry<Catalog> {
                        SceneCanvas { CatalogScreen() }
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

// Catalog route (ticket showkase-v2-01): debug builds render the new Showkase browser
// inline — it follows PuzzleBoxTheme instead of the old pinned-dark activity — while
// release keeps the stock token catalog. Main cannot reference debug-only code at
// compile time (the debug source set doesn't exist in release), so the browser is
// registered reflectively: loading CatalogDebugBinding runs its class-init, which
// assigns DebugCatalogContent. The FLAG_DEBUGGABLE gate keeps release from ever
// touching the reflection path.
internal object DebugCatalogContent {
    @Volatile
    var browser: (@Composable () -> Unit)? = null
}

private const val DEBUG_CATALOG_BINDING =
    "com.ivandsky.puzzlebox.app.debug.CatalogDebugBinding"

@Composable
private fun CatalogScreen() {
    val debugBrowser = debugCatalogBrowser()
    if (debugBrowser != null) {
        debugBrowser()
    } else {
        TokenCatalogScreen()
    }
}

@Composable
private fun debugCatalogBrowser(): (@Composable () -> Unit)? {
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
