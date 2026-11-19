package com.ivandsky.puzzlebox.app

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// DrainTestApplication for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = DrainTestApplication::class)
class ShareFileProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val authority = "${context.packageName}.share"

    // One test on purpose: FileProvider caches its roots per authority for the process, while
    // Robolectric gives every test its own data directory, so a second test would see stale roots.
    @Test
    fun `the share provider exposes the share cache directory and nothing else`() {
        val shared = cacheFile("share/recap.png")
        val other = cacheFile("other/secret.txt")

        val uri = FileProvider.getUriForFile(context, authority, shared)

        assertEquals("content", uri.scheme)
        assertEquals(authority, uri.authority)
        assertEquals("/share/recap.png", uri.path)
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, other)
        }
    }

    private fun cacheFile(path: String): File = File(context.cacheDir, path).apply {
        parentFile!!.mkdirs()
        writeBytes(byteArrayOf(1))
    }
}
