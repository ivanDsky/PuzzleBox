package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.common.flags.DeclaredFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.history.flags.HistoryFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun clearOverrides() {
        RuntimeEnvironment.getApplication().getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

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

    /** The Release flags section lists the app's declared flags, set by set in owner order; the central enum keeps its own order. */
    @Test
    fun `debug graph declares the central flags in their enum order`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            val declared = koin.get<DeclaredFlags>().all
            assertEquals(ReleaseFlag.entries, declared.filterIsInstance<ReleaseFlag>())
            assertEquals(declared, koin.get<DebugReleaseFlags>().declared)
        } finally {
            stopKoin()
        }
    }

    /** An owner enum whose set the graph never registers reads as off even where its default is on; this catches it. */
    @Test
    fun `debug graph declares every owner enum's flags`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            val keys = koin.get<DeclaredFlags>().all.map { it.key }
            assertTrue(keys.containsAll(HistoryFlag.entries.map { it.key }))
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

        val flags = DebugReleaseFlags(context, ReleaseFlag.entries)

        assertEquals(emptyMap<ReleaseFlag, Boolean>(), flags.overrides.value)
        assertEquals(ReleaseFlag.entries.filter { it.releaseDefault }.toSet(), flags.enabled.value)
    }

    /**
     * Characterization: the override store's format is file `release_flags`, key = the enum entry's own `name`, a
     * Boolean. The key is compared with `name`, never with a key the production code derives, so a change that stores
     * under anything else fails here. Renaming an entry loses its saved debug override; that is accepted (debug only).
     */
    @Test
    fun `an override is stored under the flag's name in release_flags and reset removes it`() {
        assertTrue(NO_FLAG, ReleaseFlag.entries.isNotEmpty())
        val flag = ReleaseFlag.entries.first()
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
        val flags = DebugReleaseFlags(context, ReleaseFlag.entries)

        flags.setOverride(flag, !flag.releaseDefault)

        assertEquals(mapOf(flag.name to !flag.releaseDefault), prefs.all)

        flags.setOverride(flag, null)

        assertFalse(prefs.contains(flag.name))
        assertEquals(emptyMap<String, Any?>(), prefs.all)
    }

    /**
     * Characterization: an override saved by an earlier launch under the enum entry's own `name` still forces the
     * flag. Renaming an entry loses its saved debug override; that is accepted (debug only).
     */
    @Test
    fun `a Boolean stored under a flag's name before launch is that flag's override`() {
        assertTrue(NO_FLAG, ReleaseFlag.entries.isNotEmpty())
        val flag = ReleaseFlag.entries.first()
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(flag.name, !flag.releaseDefault).commit()

        val flags = DebugReleaseFlags(context, ReleaseFlag.entries)

        assertEquals(mapOf(flag to !flag.releaseDefault), flags.overrides.value)
        val defaults = ReleaseFlag.entries.filter { it.releaseDefault }.toSet()
        val expected = if (flag.releaseDefault) defaults - flag else defaults + flag
        assertEquals(expected, flags.enabled.value)
    }

    private companion object {
        const val NO_FLAG = "no release flag: move this test to a test-only flag"
    }
}
