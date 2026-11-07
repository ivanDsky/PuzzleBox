package com.ivandsky.puzzlebox.create.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/** Registers Create's keys for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.createNavKeys() {
    subclass(CreateKey::class, CreateKey.serializer())
    subclass(EditorKey::class, EditorKey.serializer())
}
