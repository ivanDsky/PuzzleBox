package com.ivandsky.puzzlebox.app.debug

import com.ivandsky.puzzlebox.app.DebugCatalogContent

/**
 * Debug→main bridge for the Catalog route (ticket showkase-v2-01): :app main must hold
 * no compile-time reference to debug-only code (release compiles without this source
 * set), so main resolves THIS class by name and class initialization performs the
 * registration. Nothing else may reference it.
 */
object CatalogDebugBinding {
    init {
        DebugCatalogContent.browser = { ShowkaseBrowserScreen() }
    }
}
