package com.ivandsky.puzzlebox.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.nio.IntBuffer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Runs the Robolectric NATIVE graphics paths (draw, pixel readback, `IntBuffer` copy, PNG
 * encode) once, early in the `:app` test JVM. Without a warm-up the JVM aborted (SIGABRT,
 * `JniConstants: Class not found: java/nio/IntBuffer`) at the first e2e class that reached the
 * native runtime cold; the screenshot goldens used to warm it up by accident, and the default
 * run no longer executes them (see AndroidRoborazziConventionPlugin). Gradle does not promise
 * this class runs first (nor at all under `--tests`), so the first Coil decode is warmed by
 * [DrainTestApplication] too (#1325).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class)
class NativeGraphicsWarmUpTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `native graphics draw, readback and encode run`() {
        composeRule.setContent { Box(Modifier.size(8.dp).background(Color.Red)) }
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        bitmap.getPixels(IntArray(bitmap.width * bitmap.height), 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.copyPixelsToBuffer(IntBuffer.allocate(bitmap.width * bitmap.height))
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(png.size() > 0)
    }
}
