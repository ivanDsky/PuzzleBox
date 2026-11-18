package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** The graph's RandomPuzzleEntry carries the RandomPuzzle flag, off without it or without any ReleaseFlags (epic #1153). */
class RandomPuzzleEntryTest {
    @After
    fun tearDown() = stopKoin()

    @Test
    fun `the default flags give a holder that is off`() = assertEquals(false, enabled(FakeReleaseFlags()))

    @Test
    fun `the flag on gives a holder that is on`() = assertEquals(true, enabled(FakeReleaseFlags(ReleaseFlag.RandomPuzzle)))

    @Test
    fun `another flag on leaves it off`() = assertEquals(false, enabled(FakeReleaseFlags(ReleaseFlag.ShareResult)))

    @Test
    fun `no ReleaseFlags bound gives a holder that is off`() = assertEquals(false, enabled(null))

    private fun enabled(flags: ReleaseFlags?): Boolean {
        stopKoin() // tests share the JVM-global Koin context
        val koin = startKoin { modules(playModule, module { if (flags != null) single<ReleaseFlags> { flags } }) }.koin
        return koin.get<RandomPuzzleEntry>().enabled
    }
}
