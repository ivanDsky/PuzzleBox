package com.ivandsky.puzzlebox.history.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/**
 * Full history browser over the captured days (A8) — pushed from Home's teaser or from a
 * game's archive page. With a [gameId] (a [com.ivandsky.puzzlebox.model.GameId] value) it
 * opens filtered to [day] (an ISO date; null means today) and, where a detail pane shows,
 * on that game's row for the day. Without one it opens unfiltered. Serial name pinned to
 * the pre-split class name (saved state).
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.app.nav.History")
data class HistoryKey(val gameId: String? = null, val day: String? = null) : NavKey

/** Registers History's keys for back-stack saved state. */
fun PolymorphicModuleBuilder<NavKey>.historyNavKeys() {
    subclass(HistoryKey::class, HistoryKey.serializer())
    subclass(HistoryRootKey::class, HistoryRootKey.serializer())
}
