package com.ivandsky.puzzlebox.history.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/**
 * Full history browser over the captured days (A8) — pushed from Home's teaser. [gameId]
 * is the game whose tab opens (a [com.ivandsky.puzzlebox.model.GameId] value); null opens
 * every game's. Serial name pinned to the pre-split class name (saved state).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.History")
data class HistoryKey(val gameId: String? = null) : NavKey

/** Registers History's keys for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.historyNavKeys() {
    subclass(HistoryKey::class, HistoryKey.serializer())
    subclass(HistoryRootKey::class, HistoryRootKey.serializer())
}
