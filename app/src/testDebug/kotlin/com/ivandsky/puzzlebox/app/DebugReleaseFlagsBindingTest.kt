package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
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
    fun `a stored override named for a deleted flag is ignored`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("ZipEditor", false).putBoolean("TangoEditor", false).putBoolean("SudokuEditor", false)
            .putBoolean("StrandsEditor", false).putBoolean("BoardErgonomics", false).commit()

        val flags = DebugReleaseFlags(context)

        assertEquals(emptyMap<ReleaseFlag, Boolean>(), flags.overrides.value)
        assertEquals(ReleaseFlag.entries.filter { it.releaseDefault }.toSet(), flags.enabled.value)
    }

    /** Characterization: the override store's format is file `release_flags`, key = the entry's name, a Boolean. */
    @Test
    fun `an override is stored under the flag's name in release_flags and reset removes it`() {
        assumeTrue("no release flag in flight", ReleaseFlag.entries.isNotEmpty())
        val flag = ReleaseFlag.entries.first()
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
        val flags = DebugReleaseFlags(context)

        flags.setOverride(flag, !flag.releaseDefault)

        assertEquals(mapOf(flag.name to !flag.releaseDefault), prefs.all)

        flags.setOverride(flag, null)

        assertFalse(prefs.contains(flag.name))
        assertEquals(emptyMap<String, Any?>(), prefs.all)
    }

    /** Characterization: an override saved by an earlier launch, under the entry's name, still forces the flag. */
    @Test
    fun `a Boolean stored under a flag's name before launch is that flag's override`() {
        assumeTrue("no release flag in flight", ReleaseFlag.entries.isNotEmpty())
        val flag = ReleaseFlag.entries.first()
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(flag.name, !flag.releaseDefault).commit()

        val flags = DebugReleaseFlags(context)

        assertEquals(mapOf(flag to !flag.releaseDefault), flags.overrides.value)
        val defaults = ReleaseFlag.entries.filter { it.releaseDefault }.toSet()
        val expected = if (flag.releaseDefault) defaults - flag else defaults + flag
        assertEquals(expected, flags.enabled.value)
    }
}
