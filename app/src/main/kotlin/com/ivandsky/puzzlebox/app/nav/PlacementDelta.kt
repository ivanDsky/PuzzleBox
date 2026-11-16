package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import kotlin.reflect.KClass

/**
 * A flag's placement while it is in flight (ADR-0027 §12): one override row per key the flag
 * moves, read over the base table ([placementRows]) by [placementOf]. Never a second table.
 * Cleanup folds the rows into the base table and deletes the delta.
 */
internal class PlacementDelta(rows: List<PlacementRow>) {

    private val overrides: Map<KClass<out NavKey>, Placement> = rows.associate { it.key to it.placement }

    /** The override for [key], or null when this delta leaves the key on its base row. */
    operator fun get(key: KClass<out NavKey>): Placement? = overrides[key]

    companion object {
        /** No flag in flight: the base table applies unchanged. */
        val None = PlacementDelta(emptyList())
    }
}

/**
 * CustomInGames on: the Studio is full-window on Games, and the published page is pushed on
 * Games and keeps the bar.
 */
internal val customInGamesPlacement = PlacementDelta(
    listOf(
        PlacementRow(EditorKey::class, Placement(Home.PushedOn(Tab.Games), Chrome.Immersive)),
        PlacementRow(PublishedPuzzleKey::class, Placement(Home.PushedOn(Tab.Games), Chrome.Bar)),
    ),
)
