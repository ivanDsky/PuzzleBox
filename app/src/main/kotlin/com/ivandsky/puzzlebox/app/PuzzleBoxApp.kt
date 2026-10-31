package com.ivandsky.puzzlebox.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
    // metrics. One policy call drives both the nav shape and Home's pane arrangement,
    // and LocalConfiguration keeps a live rotation recomposing without a restart.
    val configuration = LocalConfiguration.current
    val layout = homeLayoutFor(
        widthSizeClass = windowWidthSizeClass,
        isLandscape = configuration.screenWidthDp > configuration.screenHeightDp,
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
                entryProvider = entryProvider {
                    entry<Home> {
                        HomeRoute(
                            layout = layout,
                            themeMode = themeMode,
                            onSetThemeMode = onSetThemeMode,
                        )
                    }
                    entry<Play> { PlayScreen() }
                    entry<Profile> { ProfileScreen(onOpenCatalog = { backStack.add(Catalog) }) }
                    entry<Catalog> { TokenCatalogScreen() }
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
