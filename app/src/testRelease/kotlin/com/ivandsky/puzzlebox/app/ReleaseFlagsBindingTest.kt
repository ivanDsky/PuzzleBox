package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.DeclaredFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import com.ivandsky.puzzlebox.common.flags.TopBarFlag
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

/**
 * Release reads only the shipped defaults of every declared flag, core:common's top-bar flags included: no override
 * path can reach a release build (ADR-0022).
 */
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
            val flags = koin.get<ReleaseFlags>()
            val declared = koin.get<DeclaredFlags>().all
            assertTrue("release binds $flags", flags is ShippedReleaseFlags)
            assertTrue("the top-bar flags are declared", declared.containsAll(TopBarFlag.entries))
            assertEquals(declared.filter { it.releaseDefault }.toSet(), flags.enabled.value)
        } finally {
            stopKoin()
        }
    }
}
