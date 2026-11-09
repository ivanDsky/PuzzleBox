package com.ivandsky.puzzlebox.compete.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Registers the compete feature's keys for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.competeNavKeys() {
    subclass(DuelKey::class, DuelKey.serializer())
    subclass(DuelRevealKey::class, DuelRevealKey.serializer())
}
