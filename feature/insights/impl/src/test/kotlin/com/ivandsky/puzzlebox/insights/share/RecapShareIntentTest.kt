package com.ivandsky.puzzlebox.insights.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The chooser a share starts: the image's content Uri with a read grant, and the text with its link (A22, ticket 11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecapShareIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uri = Uri.parse("content://${RecapShareUris.authority(context.packageName)}/share/recap-2026-11.png")
    private val text = "My PuzzleBox November 2026: 42 puzzles solved. Get PuzzleBox: $RecapShareLink"

    private val send: Intent
        get() {
            val chooser = RecapShareIntent.create(uri, text)
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION")
            return chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        }

    @Test
    fun `the authority is the application id plus share`() {
        assertEquals("${context.packageName}.share", RecapShareUris.authority(context.packageName))
        assertEquals("${context.packageName}.share", uri.authority)
    }

    @Test
    fun `the intent sends one png by its content Uri`() {
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertEquals("content", uri.scheme)
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
    }

    @Test
    fun `the intent grants the receiver read access to the file`() {
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotEquals(0, RecapShareIntent.create(uri, text).flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test
    fun `the intent carries the text and the link`() {
        assertEquals(text, send.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains(RecapShareLink))
    }
}
