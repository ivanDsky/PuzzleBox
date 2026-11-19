package com.ivandsky.puzzlebox.insights.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recap image lives in `cacheDir/share/`, one file at a time: the previous one goes on the next share and at app start. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RecapShareRendererTest {

    private val cache: File = Files.createTempDirectory("recap-share").toFile()
    private val dispatcher = StandardTestDispatcher()
    private val renderer = RecapShareRenderer(cache, FixedDispatcherProvider(dispatcher))
    private val shareDirectory = File(cache, "share")

    private fun image(width: Int = 8, height: Int = 12) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

    @After
    fun tearDown() {
        cache.deleteRecursively()
    }

    @Test
    fun `a render writes a decodable png under the share directory`() = runTest(dispatcher) {
        val file = renderer.render(image(), "recap-2026-11")

        assertEquals(File(shareDirectory, "recap-2026-11.png"), file)
        val decoded = BitmapFactory.decodeFile(file.path)
        assertEquals(8, decoded.width)
        assertEquals(12, decoded.height)
    }

    @Test
    fun `the next render deletes the previous image`() = runTest(dispatcher) {
        val first = renderer.render(image(), "recap-2026-10")

        val second = renderer.render(image(), "recap-2026-11")

        assertFalse(first.exists())
        assertTrue(second.exists())
        assertEquals(listOf("recap-2026-11.png"), shareDirectory.list()!!.toList())
    }

    @Test
    fun `clear deletes what a share left and is harmless without a directory`() = runTest(dispatcher) {
        renderer.clear()
        val file = renderer.render(image(), "recap-2026-11")

        renderer.clear()

        assertFalse(file.exists())
    }

    @Test
    fun `starting the cleaner deletes the previous image`() = runTest(dispatcher) {
        val file = renderer.render(image(), "recap-2026-11")

        RecapShareCleaner(renderer, this).start()
        testScheduler.advanceUntilIdle()

        assertFalse(file.exists())
    }
}
