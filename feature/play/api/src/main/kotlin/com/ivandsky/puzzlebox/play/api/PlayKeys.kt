package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Registers Play's keys (play, result, replay) for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.playNavKeys() {
    subclass(PlayKey::class, PlayKey.serializer())
    subclass(ResultKey::class, ResultKey.serializer())
    subclass(ReplayKey::class, ReplayKey.serializer())
}
