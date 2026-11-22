package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.os.Looper
import androidx.glance.appwidget.AppWidgetId
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import java.time.LocalDate
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * A real Glance session over the real [PuzzleBoxWidget]: the session composes the widget once per responsive size
 * ([WidgetSizes.ALL]), so a read started inside the composition runs once per size. The read is the slow, cache-filling
 * part, so it must run once for the session, not once per size (#1999: 15 parallel reads each timed out at 8 s).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class PuzzleBoxWidgetSessionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Glance starts a session through WorkManager, which nothing initialises under Robolectric here and which is on the
     * test runtime classpath only (transitively, via Glance), not the compile one, so it is initialised by reflection.
     */
    @Before
    fun startWorkManager() {
        val builder = Class.forName("androidx.work.Configuration\$Builder").getConstructor().newInstance()
        builder.javaClass.getMethod("setExecutor", Executor::class.java).invoke(builder, Executors.newCachedThreadPool())
        // Without a default process name WorkManager treats the test as a secondary process and never starts the session.
        builder.javaClass.getMethod("setDefaultProcessName", String::class.java).invoke(builder, context.packageName)
        val config = builder.javaClass.getMethod("build").invoke(builder)
        Class.forName("androidx.work.WorkManager")
            .getMethod("initialize", Context::class.java, Class.forName("androidx.work.Configuration"))
            .invoke(null, context, config)
    }

    @Test
    fun `a session reads once however many sizes it composes`() {
        val reads = AtomicInteger()
        val widget = PuzzleBoxWidget(
            CachedWidgetReader(
                read = {
                    reads.incrementAndGet()
                    WidgetSamples.default
                },
                store = MemoryWidgetSnapshotStore(),
                puzzleDay = { LocalDate.parse("2026-11-21") },
            ),
        )

        runBlocking { widget.update(context, AppWidgetId(1)) }
        val deadline = System.nanoTime() + 20_000_000_000L
        while (reads.get() == 0 && System.nanoTime() < deadline) {
            shadowLooperIdle()
            Thread.sleep(50)
        }
        repeat(60) {
            shadowLooperIdle()
            Thread.sleep(50)
        }

        assertEquals("reads per session over ${WidgetSizes.ALL.size} sizes", 1, reads.get())
    }

    private fun shadowLooperIdle() = ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
}
