package com.ivandsky.puzzlebox.profile.whatsnew

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The asset source: the packaged `whats_new.txt` reads through the app's own assets, and a broken file propagates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AssetWhatsNewSourceTest {

    @Test
    fun `the packaged file reads through the context's assets, newest first`() {
        val releases = AssetWhatsNewSource(ApplicationProvider.getApplicationContext<Context>()).releases()

        assertTrue(releases.isNotEmpty())
        assertEquals("0.36.2", releases.first().version)
        assertTrue(releases.all { it.notes.isNotEmpty() })
    }

    @Test
    fun `a missing asset throws instead of returning an empty list`() {
        val source = AssetWhatsNewSource(contextWith { throw FileNotFoundException("whats_new.txt") })

        assertThrows(FileNotFoundException::class.java) { source.releases() }
    }

    @Test
    fun `a malformed asset throws the parser's error`() {
        val source = AssetWhatsNewSource(contextWith { ByteArrayInputStream("- a note before any release".toByteArray()) })

        assertThrows(IllegalArgumentException::class.java) { source.releases() }
    }

    private fun contextWith(open: () -> java.io.InputStream): Context {
        val assets = mock(AssetManager::class.java)
        `when`(assets.open("whats_new.txt")).thenAnswer { open() }
        return mock(Context::class.java).also { `when`(it.assets).thenReturn(assets) }
    }
}
