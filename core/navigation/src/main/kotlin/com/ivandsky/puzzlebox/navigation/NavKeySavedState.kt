package com.ivandsky.puzzlebox.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The back stack's saved-state configuration: NavKeys live in many feature api modules,
 * so restore goes through open polymorphism over [NavKey] with every module's keys
 * registered by [registerKeys] (each api exposes its own registration).
 *
 * Saved form: each entry is `type` (the key's serial name) + `value` — the same shape the
 * reflective default writes with the class name as `type`. Serial names carry the
 * `com.ivandsky.puzzlebox.app.nav.` prefix by convention; compatibility across releases is
 * versioned by `NAVIGATION_STATE_KEY` (ADR-0011 §5), not by pinned names.
 */
fun navKeySavedStateConfiguration(
    registerKeys: PolymorphicModuleBuilder<NavKey>.() -> Unit,
): SavedStateConfiguration = SavedStateConfiguration {
    // build() layers this over savedstate's default module (overwriteWith).
    serializersModule = SerializersModule { polymorphic(NavKey::class) { registerKeys() } }
}
