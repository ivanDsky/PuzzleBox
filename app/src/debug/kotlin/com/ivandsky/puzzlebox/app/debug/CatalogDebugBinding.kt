package com.ivandsky.puzzlebox.app.debug

import android.content.Context
import com.ivandsky.puzzlebox.app.DebugCatalogContent
import com.ivandsky.puzzlebox.app.debug.design.DesignVariantsPrefs
import com.ivandsky.puzzlebox.designsystem.DesignVariantsStore

/**
 * Debug→main bridge for the Catalog route and Settings' debug sections: :app main must hold
 * no compile-time reference to debug-only code (release compiles without this source
 * set), so main resolves THIS class by name and class initialization performs the
 * registration. Nothing else may reference it.
 */
object CatalogDebugBinding {
    init {
        DebugCatalogContent.browser = { ShowkaseBrowserScreen() }
        DebugCatalogContent.settingsSections = { openGame -> DebugSettingsSections(openGame) }
    }

    /**
     * Startup hook (spec A7): PuzzleBoxApplication calls this reflectively in debuggable
     * builds so the saved developer design settings apply from the first frame.
     */
    @JvmStatic
    fun install(context: Context) {
        DesignVariantsStore.current = DesignVariantsPrefs(context).load()
    }
}
