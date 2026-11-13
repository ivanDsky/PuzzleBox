package com.ivandsky.puzzlebox.play

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.navKeySavedStateConfiguration
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.playNavKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The replay key is ids only, so it restores after process death for every target the later surfaces use. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayKeyTest {

    @Test
    fun `every target survives a saved-state round trip`() {
        val keys: List<NavKey> = listOf(
            ReplayKey.daily("queens", "2026-11-04", 7, latest = true),
            ReplayKey.daily("queens", "2026-11-04", null, latest = false),
            ReplayKey.custom("queens", "abc", latest = true),
            ReplayKey.leg("queens", "M8T3VQ", legIndex = 1, theirs = true),
            ReplayKey.player("queens", "2026-11-04", "uid1", "Maya"),
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
}
