package com.ivandsky.puzzlebox.play.flags

import com.ivandsky.puzzlebox.common.flags.FlagSet
import com.ivandsky.puzzlebox.play.di.playModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication

/** The solve counter ships on, the create FAB still dark (ADR-0022 §4), and the enum reaches the app's [FlagSet]s through [playModule]. */
class PlayFlagTest {
    @Test
    fun `solve counter is on in release`() {
        assertTrue(PlayFlag.SolveCounter.releaseDefault)
    }

    @Test
    fun `create fab is off in release`() {
        assertFalse(PlayFlag.CreateFab.releaseDefault)
    }

    @Test
    fun `playModule registers every play flag as the play set`() {
        val koin = koinApplication { modules(playModule) }.koin
        val set = koin.get<FlagSet>(named("flags.play"))
        assertEquals("play", set.owner)
        assertEquals(PlayFlag.entries, set.flags)
    }
}
