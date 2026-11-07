package com.ivandsky.puzzlebox.app.debug.design

import android.content.Context
import com.ivandsky.puzzlebox.designsystem.DesignVariants

/**
 * Persists the developer design settings (spec A7) across launches. SharedPreferences
 * like ShowkaseBrowserPrefs: :app declares no DataStore and this class never leaves the
 * debug variant. A missing or unknown value falls back to [DesignVariants.Default].
 */
internal class DesignVariantsPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    fun load(): DesignVariants {
        val default = DesignVariants.Default
        return DesignVariants(
            glyphSolid = prefs.getBoolean(KEY_GLYPH_SOLID, default.glyphSolid),
            sweepMs = prefs.getInt(KEY_SWEEP_MS, default.sweepMs),
        )
    }

    fun save(variants: DesignVariants) {
        prefs.edit()
            .putBoolean(KEY_GLYPH_SOLID, variants.glyphSolid)
            .putInt(KEY_SWEEP_MS, variants.sweepMs)
            .apply()
    }

    private companion object {
        const val PREFERENCES_FILE = "design_variants"
        const val KEY_GLYPH_SOLID = "glyph_solid"
        const val KEY_SWEEP_MS = "sweep_ms"
    }
}
