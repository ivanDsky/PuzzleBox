package com.ivandsky.puzzlebox.history.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/**
 * Full history browser over the captured days (A8) — pushed from Home's teaser. Serial
 * name pinned to the pre-split class name (saved state).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.History")
data object HistoryKey : NavKey

/** Registers History's keys for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.historyNavKeys() {
    subclass(HistoryKey::class, HistoryKey.serializer())
}
