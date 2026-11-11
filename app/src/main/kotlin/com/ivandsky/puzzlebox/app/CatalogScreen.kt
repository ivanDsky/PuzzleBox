package com.ivandsky.puzzlebox.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.app.screens.CatalogUnavailableScreen

// Debug builds render the Showkase browser inline (it follows PuzzleBoxTheme) and put the
// debug tools in Settings. Main cannot reference debug-only code at compile time (the
// debug source set doesn't exist in release), so both are registered reflectively: loading
// CatalogDebugBinding runs its class-init, which assigns them here. The FLAG_DEBUGGABLE gate
// keeps release from ever touching the reflection path; anything that can't or shouldn't
// load them (release, non-debuggable) gets the cutover placeholder for the browser and no
// Settings sections.
internal object DebugCatalogContent {
    @Volatile
    var browser: (@Composable () -> Unit)? = null

    @Volatile
    var settingsSections: (@Composable (openGame: (String) -> Unit) -> Unit)? = null
}

private const val DEBUG_CATALOG_BINDING =
    "com.ivandsky.puzzlebox.app.debug.CatalogDebugBinding"

@Composable
internal fun CatalogScreen() {
    val debugBrowser = debugCatalogContent()?.browser
    Box(Modifier.testTag("route.catalog")) {
        if (debugBrowser != null) {
            debugBrowser()
        } else {
            CatalogUnavailableScreen()
        }
    }
}

/** Settings' debug sections: nothing outside debuggable builds. */
@Composable
internal fun DebugSettingsSlot(openGame: (String) -> Unit) {
    debugCatalogContent()?.settingsSections?.invoke(openGame)
}

@Composable
private fun debugCatalogContent(): DebugCatalogContent? {
    val isDebuggable = LocalContext.current.applicationInfo.flags and
        android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    return if (isDebuggable) {
        remember {
            try {
                // Class.forName initializes the class, running the binding's registration.
                Class.forName(DEBUG_CATALOG_BINDING)
                DebugCatalogContent
            } catch (_: Throwable) {
                null
            }
        }
    } else {
        null
    }
}
