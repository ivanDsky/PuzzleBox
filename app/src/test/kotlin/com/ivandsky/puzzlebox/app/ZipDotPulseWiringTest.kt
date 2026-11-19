package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Characterization: on the real app graph, [PlayFlag.ZipDotPulse] alone decides the `ZipDotPulse` holder the Zip board
 * reads. The holder is internal to :feature:play:impl, so it is resolved and read by name. Delete this test with the
 * ZipDotPulse cleanup ticket.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ZipDotPulseWiringTest {

    @Test
    fun `zip dot pulse on turns on the holder`() {
        assertTrue(enabledWith(FakeReleaseFlags(PlayFlag.ZipDotPulse)))
    }

    @Test
    fun `zip dot pulse off turns off the holder`() {
        assertFalse(enabledWith(FakeReleaseFlags()))
    }

    private fun enabledWith(flags: ReleaseFlags): Boolean {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<IdentityService> { FakeIdentityService() }
                    single<ReleaseFlags> { flags }
                },
            )
        }.koin
        try {
            val holder = koin.get<Any>(Class.forName(HOLDER).kotlin)
            return holder.javaClass.getMethod("getEnabled").invoke(holder) as Boolean
        } finally {
            stopKoin()
        }
    }

    private companion object {
        const val HOLDER = "com.ivandsky.puzzlebox.play.ZipDotPulse"
    }
}
