package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
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
 * CommunityPuzzles on: Custom's list is the Community tab, so everything it opens is pushed on
 * Community and keeps its chrome: the Studio is full-window, the published page and a friend's puzzle keep the bar,
 * and a custom board and its result are immersive. Games keeps the daily keys.
 */
internal val communityPlacement = PlacementDelta(
    listOf(
        PlacementRow(EditorKey::class, Placement(Home.PushedOn(Tab.Community), Chrome.Immersive)),
        PlacementRow(PublishedPuzzleKey::class, Placement(Home.PushedOn(Tab.Community), Chrome.Bar)),
        PlacementRow(FriendPuzzleKey::class, Placement(Home.PushedOn(Tab.Community), Chrome.Bar)),
        PlacementRow(CustomGameKey::class, Placement(Home.PushedOn(Tab.Community), Chrome.Immersive)),
        PlacementRow(CustomResultKey::class, Placement(Home.PushedOn(Tab.Community), Chrome.Immersive)),
    ),
)
