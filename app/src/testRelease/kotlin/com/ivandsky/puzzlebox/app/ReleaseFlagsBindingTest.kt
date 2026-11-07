package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import org.junit.Assert.assertSame
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
@Config(sdk = [34], application = android.app.Application::class)
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
}
