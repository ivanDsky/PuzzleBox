package com.ivandsky.puzzlebox.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.app.screens.CatalogUnavailableScreen

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
@Composable (openGame: (String) -> Unit) -> Unit)? = null
}

private const val DEBUG_CATALOG_BINDING =
    "com.ivandsky.puzzlebox.app.debug.CatalogDebugBinding"
@Composable
internal fun CatalogScreen(openGame: (String) -> Unit) {
    val debugBrowser = debugCatalogBrowser()
    Box(Modifier.testTag("route.catalog")) {
        if (debugBrowser != null) {
            debugBrowser(openGame)
        } else {
            CatalogUnavailableScreen()
        }
    }
}

@Composable
private fun debugCatalogBrowser(): (
@Composable (openGame: (String) -> Unit) -> Unit)? {
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
