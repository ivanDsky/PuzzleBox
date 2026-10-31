package com.ivandsky.puzzlebox.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Robolectric because the production artifact is Preferences DataStore; the file it
 * writes is a plain temp file, so a fresh repository over the same file is an exact
 * stand-in for a new process reading persisted state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreDebugStyleRepositoryTest {

    private fun newRepository(file: File): DataStoreDebugStyleRepository =
        DataStoreDebugStyleRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
            ) { file },
        )

    private fun tempFile(): File =
        File(
            RuntimeEnvironment.getApplication().cacheDir,
            "debug-style-${UUID.randomUUID()}.preferences_pb",
        )

    @Test
    fun `first launch has no stored variants`() = runTest {
        val repository = newRepository(tempFile())

        assertNull(repository.typographyVariantName.first())
        assertNull(repository.paletteVariantName.first())
    }

    @Test
    fun `writes round-trip both axes`() = runTest {
        val repository = newRepository(tempFile())

        repository.setTypographyVariantName("PLAYFUL")
        repository.setPaletteVariantName("CLEAN_LIGHT")

        assertEquals("PLAYFUL", repository.typographyVariantName.first())
        assertEquals("CLEAN_LIGHT", repository.paletteVariantName.first())

        // Re-picking overwrites independently.
        repository.setTypographyVariantName("EDITORIAL")
        assertEquals("EDITORIAL", repository.typographyVariantName.first())
        assertEquals("CLEAN_LIGHT", repository.paletteVariantName.first())
    }

    @Test
    fun `style choice survives a new repository over the same file`() = runTest {
        val file = tempFile()
        // DataStore allows only one live instance per file — one per process. Cancelling
        // the first scope ends the "process"; the restarted process re-reads the file.
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val firstProcess = DataStoreDebugStyleRepository(
            PreferenceDataStoreFactory.create(scope = firstScope) { file },
        )

        firstProcess.setTypographyVariantName("CLEAN")
        firstProcess.setPaletteVariantName("PLAYFUL_SATURATED")
        firstScope.cancel()

        val restartedProcess = newRepository(file)
        assertEquals("CLEAN", restartedProcess.typographyVariantName.first())
        assertEquals("PLAYFUL_SATURATED", restartedProcess.paletteVariantName.first())
    }
}
