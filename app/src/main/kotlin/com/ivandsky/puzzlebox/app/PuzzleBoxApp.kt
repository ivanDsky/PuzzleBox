package com.ivandsky.puzzlebox.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.ivandsky.puzzlebox.app.nav.PuzzleBoxNavShell
import com.ivandsky.puzzlebox.app.nav.TopLevelNavBar
import com.ivandsky.puzzlebox.app.nav.TopLevelNavRail
import com.ivandsky.puzzlebox.app.nav.rememberNavigator
import com.ivandsky.puzzlebox.app.screens.CatalogUnavailableScreen
import com.ivandsky.puzzlebox.gameui.playLayoutFor
import com.ivandsky.puzzlebox.history.historyLayoutFor
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.homeLayoutFor
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * The app shell: tab bar (or rail) over [PuzzleBoxNavShell]. The highlight is the
 * [navigator]'s selected tab, so it always names the tab that owns the screen on top.
 */
@Composable
fun PuzzleBoxApp(
    windowWidthSizeClass: WindowWidthSizeClass,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    navigator: Navigator = rememberNavigator(),
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
    val selected = navigator.state.tab

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Two-pane moves navigation to a rail; the bottom bar is every other shape.
            if (!expanded) {
                TopLevelNavBar(selected = selected, onSelect = navigator::selectTab)
            }
        },
    ) { innerPadding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (expanded) {
                TopLevelNavRail(selected = selected, onSelect = navigator::selectTab)
            }
            PuzzleBoxNavShell(
                navigator = navigator,
                homeLayout = layout,
                playLayout = playLayout,
                historyLayout = historyLayout,
                themeMode = themeMode,
                onSetThemeMode = onSetThemeMode,
                modifier = Modifier.weight(1f),
            )
        }
    }
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
internal fun CatalogScreen() {
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
