package com.ivandsky.puzzlebox.play.flags

import com.ivandsky.puzzlebox.common.flags.FlagSet
import com.ivandsky.puzzlebox.play.di.playModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication

/** Community shelves ships dark (ADR-0022 §4) and its enum reaches the app's [FlagSet]s through [playModule]. */
class PlayFlagTest {
    @Test
    fun `community shelves is off in release`() {
        assertFalse(PlayFlag.CommunityShelves.releaseDefault)
    }

    @Test
    fun `playModule registers every play flag as the play set`() {
        val koin = koinApplication { modules(playModule) }.koin
        val set = koin.get<FlagSet>(named("flags.play"))
        assertEquals("play", set.owner)
        assertEquals(PlayFlag.entries, set.flags)
    }
}
