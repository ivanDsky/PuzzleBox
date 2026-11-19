package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Characterization: on [playModule], [PlayFlag.ZipDotPulse] alone decides the [ZipDotPulse] holder the Zip board reads.
 * Delete this test with the ZipDotPulse cleanup ticket.
 */
class ZipDotPulseWiringTest {

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `zip dot pulse on turns on the holder`() {
        assertTrue(enabledWith(FakeReleaseFlags(PlayFlag.ZipDotPulse)))
    }

    @Test
    fun `zip dot pulse off turns off the holder`() {
        assertFalse(enabledWith(FakeReleaseFlags()))
    }

    private fun enabledWith(flags: ReleaseFlags): Boolean {
        stopKoin() // tests share the JVM-global Koin context
        val koin = startKoin { modules(playModule, module { single<ReleaseFlags> { flags } }) }.koin
        return koin.get<ZipDotPulse>().enabled
    }
}
