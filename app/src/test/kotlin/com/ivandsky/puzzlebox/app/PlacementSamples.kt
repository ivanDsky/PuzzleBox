package com.ivandsky.puzzlebox.app

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.nav.placementRows
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import kotlin.reflect.KClass

/**
 * One instance of every key class in the placement table, so a test can build a key from a
 * row. Objects are their own instance; a parameterised key needs a sample here, and a new
 * row without one fails with a message naming the class.
 */
internal object PlacementSamples {

    private val parameterised: Map<KClass<out NavKey>, NavKey> = listOf<NavKey>(
        HistoryKey(),
        GameKey("zip", "2026-03-09"),
        ResultKey("zip", "2026-03-09", 61_200, 2),
        ReplayKey.daily("zip", "2026-03-09", 2, latest = true),
        CustomGameKey("zip", "p1"),
        CustomResultKey("zip", "p1", 61_200, 2),
        FriendPuzzleKey("K7Q2MX"),
        MatchLegKey("K7Q2MX", 0, "zip"),
        MatchLegResultKey("K7Q2MX", 0, 61_200, 0, false),
        DuelKey("K7Q2MX"),
        DuelRevealKey("K7Q2MX"),
        EditorKey("d1"),
        PublishedPuzzleKey("K7Q2MX"),
    ).associateBy { it::class }

    /** A sample instance of [key]. */
    fun of(key: KClass<out NavKey>): NavKey =
        key.objectInstance
            ?: parameterised[key]
            ?: error("No sample for ${key.qualifiedName}: add one to PlacementSamples.parameterised.")

    /** A sample of every key class in the table, in row order. */
    fun everyRow(): List<NavKey> = placementRows.map { of(it.key) }
}
