package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.common.flags.DeclaredFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.play.flags.PlayFlag
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

    /** The Release flags section lists the app's declared flags, set by set in owner order; a set keeps its enum's order. */
    @Test
    fun `debug graph declares the play feature's flags in their enum order`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            val declared = koin.get<DeclaredFlags>().all
            assertEquals(PlayFlag.entries, declared.filterIsInstance<PlayFlag>())
            assertEquals(declared, koin.get<DebugReleaseFlags>().declared)
        } finally {
            stopKoin()
        }
    }

    /**
     * An owner enum whose set the graph never registers reads as off even where its default is on, and a key that drifts
     * from its entry's name strands the stored override; this catches both without naming a live flag.
     */
    @Test
    fun `debug graph declares every owner enum's flags under their entry names`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            val declared = koin.get<DeclaredFlags>().all
            val enumBacked = declared.filterIsInstance<Enum<*>>()
            assertEquals(declared.size, enumBacked.size)
            assertEquals(enumBacked.map { it.name }, declared.map { it.key })
            val keys = declared.map { it.key }
            assertTrue(keys.containsAll(PlayFlag.entries.map { it.key }))
            assertTrue(keys.containsAll(InsightsFlag.entries.map { it.key }))
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `a stored override named for a deleted flag is ignored`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("ZipEditor", false).putBoolean("TangoEditor", false).putBoolean("SudokuEditor", false)
            .putBoolean("StrandsEditor", false).putBoolean("BoardErgonomics", false).putBoolean("SheetScreens", false).commit()

        val flags = DebugReleaseFlags(context, PlayFlag.entries)

        assertEquals(emptyMap<PlayFlag, Boolean>(), flags.overrides.value)
        assertEquals(PlayFlag.entries.filter { it.releaseDefault }.toSet(), flags.enabled.value)
    }

    /**
     * Characterization: the override store's format is file `release_flags`, key = the enum entry's own `name`, a
     * Boolean. The key is compared with `name`, never with a key the production code derives, so a change that stores
     * under anything else fails here. Renaming an entry loses its saved debug override; that is accepted (debug only).
     */
    @Test
    fun `an override is stored under the flag's name in release_flags and reset removes it`() {
        val flag = PlayFlag.entries.first()
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
        val flags = DebugReleaseFlags(context, PlayFlag.entries)

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
        val flag = PlayFlag.entries.first()
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(flag.name, !flag.releaseDefault).commit()

        val flags = DebugReleaseFlags(context, PlayFlag.entries)

        assertEquals(mapOf(flag to !flag.releaseDefault), flags.overrides.value)
        val defaults = PlayFlag.entries.filter { it.releaseDefault }.toSet()
        val expected = if (flag.releaseDefault) defaults - flag else defaults + flag
        assertEquals(expected, flags.enabled.value)
    }
}
