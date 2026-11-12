package com.ivandsky.puzzlebox.play

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.navKeySavedStateConfiguration
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.playNavKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The compare key is ids only, so it restores after process death for the pairs both surfaces open it with. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayCompareKeyTest {

    @Test
    fun `a duel leg pair and a record against latest pair survive a saved-state round trip`() {
        val keys: List<NavKey> = listOf(
            ReplayCompareKey.of(
                mine = ReplayKey.leg("queens", "M8T3VQ", 1),
                theirs = ReplayKey.leg("queens", "M8T3VQ", 1, theirs = true),
                myName = "You",
                theirName = "Maya",
                lead = ReplayCompareKey.LEAD_THEIRS,
            ),
            ReplayCompareKey.of(
                mine = ReplayKey.daily("queens", "2026-11-04", 7, latest = false),
                theirs = ReplayKey.daily("queens", "2026-11-04", 7, latest = true),
                myName = "First win",
                theirName = "Latest",
            ),
        )
        val state = NavigationState(PlayKey, listOf(listOf(PlayKey) + keys))
        val config = navKeySavedStateConfiguration { playNavKeys() }

        val restored = decodeFromSavedState(
            NavigationState.serializer(),
            encodeToSavedState(NavigationState.serializer(), state, config),
            config,
        )

        assertEquals(state, restored)
    }

    @Test
    fun `each side rebuilds the replay key it was made from`() {
        val mine = ReplayKey.daily("queens", "2026-11-04", 7, latest = false)
        val theirs = ReplayKey.daily("queens", "2026-11-04", 7, latest = true)
        val key = ReplayCompareKey.of(mine, theirs, "First win", "Latest")

        assertEquals(mine, key.mine())
        assertEquals(theirs, key.theirs())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `two different puzzles are not one compare`() {
        ReplayCompareKey.of(ReplayKey.leg("queens", "M8T3VQ", 0), ReplayKey.leg("queens", "M8T3VQ", 1, theirs = true), "You", "Maya")
    }
}
