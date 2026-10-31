package com.ivandsky.puzzlebox.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.data.repository.DebugStyleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Wiring guard for the two Preferences DataStores (ticket 09): both stores share the
 * Koin type `DataStore<Preferences>`, so every binding is qualified. Before the
 * qualifiers, the duplicate unqualified bindings made the LAST-registered store — the
 * debug one — win every `get<DataStore<Preferences>>()`, silently pointing
 * [UserPreferencesRepository] at `debug_style.preferences_pb` (theme_mode leaked into
 * the debug file, and a release build would have created it). The test pins the fixed
 * contract: each repository reads/writes only its own file, and unqualified resolution
 * no longer exists to grab silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataModuleWiringTest {

    @After
    fun tearDown() {
        if (GlobalContext.getKoinApplicationOrNull() != null) {
            GlobalContext.stopKoin()
        }
    }

    @Test
    fun `each repository resolves and writes only its own store`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        GlobalContext.startKoin { modules(module { single<android.content.Context> { app } }, commonModule, dataModule) }

        val userPreferences = GlobalContext.get().get<UserPreferencesRepository>()
        val debugStyle = GlobalContext.get().get<DebugStyleRepository>()
        assertNotNull(userPreferences)
        assertNotNull(debugStyle)

        userPreferences.setThemeMode(ThemeMode.LIGHT)
        debugStyle.setPaletteVariantName("PLAYFUL_NIGHT")

        assertEquals(ThemeMode.LIGHT, userPreferences.themeMode.first())
        assertEquals("PLAYFUL_NIGHT", debugStyle.paletteVariantName.first())

        val production = File(app.filesDir, "puzzlebox.preferences_pb")
        val debug = File(app.filesDir, "debug_style.preferences_pb")
        assertTrue("production store written", production.exists())
        assertTrue("debug store written", debug.exists())
        val productionBytes = production.readBytes().decodeToString()
        val debugBytes = debug.readBytes().decodeToString()
        assertTrue(productionBytes.contains("theme_mode"))
        assertFalse("production store must not gain debug keys", productionBytes.contains("debug_palette_variant"))
        assertTrue(debugBytes.contains("debug_palette_variant"))
        assertFalse("debug store must not gain production keys", debugBytes.contains("theme_mode"))
    }

    @Test
    fun `unqualified DataStore resolution no longer exists`() {
        val app = RuntimeEnvironment.getApplication()
        GlobalContext.startKoin { modules(module { single<android.content.Context> { app } }, commonModule, dataModule) }

        try {
            GlobalContext.get().get<DataStore<Preferences>>()
            fail("unqualified DataStore<Preferences> must not resolve — bind via named qualifiers")
        } catch (expected: NoDefinitionFoundException) {
            // the guard: no silent wrong-store resolution possible
        }
    }
}
