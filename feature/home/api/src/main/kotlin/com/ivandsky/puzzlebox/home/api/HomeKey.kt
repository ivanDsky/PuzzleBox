package com.ivandsky.puzzlebox.home.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Home tab root (top level). Serial name pinned to the pre-split class name (saved state). */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.Home")
data object HomeKey : NavKey

/** Registers Home's keys for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.homeNavKeys() {
    subclass(HomeKey::class, HomeKey.serializer())
}
