package com.ivandsky.puzzlebox.app

import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.app.debug.tuning.DebugPlayTuningSource
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.app.di.FlaggedPlayTuningSource
import com.ivandsky.puzzlebox.gameui.DeviceClass
import com.ivandsky.puzzlebox.gameui.PlayTuning
import com.ivandsky.puzzlebox.gameui.PlayTuningSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
@Config(sdk = [34], application = DrainTestApplication::class)
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
    fun `debug graph binds the flag-gated play tuning, shipped tuning by default while BoardErgonomics ships on`() {
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

    @Test
    fun `debug graph's play tuning goes dark once BoardErgonomics is overridden off`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            koin.get<DebugReleaseFlags>().setOverride(ReleaseFlag.BoardErgonomics, false)

            assertNull(koin.get<PlayTuningSource>().tuning.value)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `debug graph's play tuning holds the base value once BoardErgonomics is overridden on`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            koin.get<DebugReleaseFlags>().setOverride(ReleaseFlag.BoardErgonomics, true)

            assertEquals(PlayTuning.Shipped, koin.get<PlayTuningSource>().tuning.value)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `debug graph's play tuning follows the Settings values live once BoardErgonomics is on`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            koin.get<DebugReleaseFlags>().setOverride(ReleaseFlag.BoardErgonomics, true)
            koin.get<DebugPlayTuningSource>().setCap(DeviceClass.Phone, 400.dp)

            assertEquals(PlayTuning.Shipped.copy(phoneCap = 400.dp), koin.get<PlayTuningSource>().tuning.value)
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
