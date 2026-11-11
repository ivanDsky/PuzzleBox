package com.ivandsky.puzzlebox.profile.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Profile tab root (top level). Serial name pinned to the pre-split class name (saved state). */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Profile")
data object ProfileKey : NavKey

/** Registers Profile's keys for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.profileNavKeys() {
    subclass(ProfileKey::class, ProfileKey.serializer())
    subclass(SettingsKey::class, SettingsKey.serializer())
    subclass(LinkedInLoginKey::class, LinkedInLoginKey.serializer())
}
