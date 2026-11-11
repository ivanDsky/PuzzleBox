package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Debug gates and the Catalog's Release flags tab share one override store (ADR-0022). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DebugReleaseFlagsBindingTest {

    @Test
    fun `debug graph binds the overridable flags the Catalog tab edits`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            assertSame(koin.get<DebugReleaseFlags>(), koin.get<ReleaseFlags>())
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `a stored override named for a deleted flag is ignored`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("ZipEditor", false).putBoolean("TangoEditor", false).putBoolean("SudokuEditor", false)
            .putBoolean("StrandsEditor", false).commit()

        val flags = DebugReleaseFlags(context)

        assertEquals(emptyMap<ReleaseFlag, Boolean>(), flags.overrides.value)
        assertEquals(ReleaseFlag.entries.filter { it.releaseDefault }.toSet(), flags.enabled.value)
    }
}
