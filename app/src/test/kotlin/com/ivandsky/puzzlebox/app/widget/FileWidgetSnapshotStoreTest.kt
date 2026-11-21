package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.app.DrainTestApplication
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class FileWidgetSnapshotStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val snapshot = WidgetSnapshot(LocalDate.parse("2026-11-21"), WidgetSamples.mixed)

    private fun store(file: File, dispatcher: CoroutineDispatcher) = FileWidgetSnapshotStore(file, dispatcher)

    @Test
    fun `nothing saved yet loads as nothing`() = runTest {
        assertNull(store(File(folder.root, "widget.json"), StandardTestDispatcher(testScheduler)).load())
    }

    @Test
    fun `a saved picture loads back, also in a new store on the same file`() = runTest {
        val file = File(folder.root, "widget.json")
        store(file, StandardTestDispatcher(testScheduler)).save(snapshot)

        assertEquals(snapshot, store(file, StandardTestDispatcher(testScheduler)).load())
    }

    @Test
    fun `a damaged file loads as nothing`() = runTest {
        val file = File(folder.root, "widget.json").apply { writeText("{\"version\":1,\"day\":") }

        assertNull(store(file, StandardTestDispatcher(testScheduler)).load())
    }

    @Test
    fun `a save that cannot write leaves the caller running`() = runTest {
        val file = File(folder.root, "missing-dir/widget.json")
        folder.root.resolve("missing-dir").writeText("a file where the directory should be")

        store(file, StandardTestDispatcher(testScheduler)).save(snapshot)

        assertNull(store(file, StandardTestDispatcher(testScheduler)).load())
    }
}
