package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.app.di.FlaggedPlayTuningSource
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.gameui.PlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Release reads only the shipped defaults: no override path can reach a release build (ADR-0022). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ReleaseFlagsBindingTest {

    @Test
    fun `release graph binds the shipped release flags`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            assertSame(ShippedReleaseFlags, koin.get<ReleaseFlags>())
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `release graph binds the flag-gated play tuning, dark while BoardErgonomics ships off`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            assertTrue(koin.get<PlayTuningSource>() is FlaggedPlayTuningSource)
            assertEquals(PlayTuning.Shipped.takeIf { ReleaseFlag.BoardErgonomics.releaseDefault }, koin.get<PlayTuningSource>().tuning.value)
        } finally {
            stopKoin()
        }
    }
}
