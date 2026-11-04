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
 * reflective default writes with the class name as `type`. Keys therefore pin
 * `@SerialName` to the class name they were first saved under, so a back stack saved by
 * an older build restores after a key moves or is renamed.
 */
fun navKeySavedStateConfiguration(
    registerKeys: PolymorphicModuleBuilder<NavKey>.() -> Unit,
): SavedStateConfiguration = SavedStateConfiguration {
    // build() layers this over savedstate's default module (overwriteWith).
    serializersModule = SerializersModule { polymorphic(NavKey::class) { registerKeys() } }
}
