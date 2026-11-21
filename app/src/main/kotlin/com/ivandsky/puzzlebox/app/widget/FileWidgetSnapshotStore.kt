package com.ivandsky.puzzlebox.app.widget

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The widget's last good picture in one small JSON [file], written whole through [AtomicFile] so a process killed mid-write
 * leaves the previous picture. A missing, damaged or unreadable file loads null; a failed write is logged and dropped,
 * since the cache only saves a redraw.
 */
class FileWidgetSnapshotStore(
    file: File,
    private val io: CoroutineDispatcher,
) : WidgetSnapshotStore {

    private val atomic = AtomicFile(file)

    override suspend fun load(): WidgetSnapshot? = withContext(io) {
        try {
            WidgetSnapshotJson.decode(atomic.readFully().decodeToString())
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun save(snapshot: WidgetSnapshot): Unit = withContext(io) {
        val bytes = WidgetSnapshotJson.encode(snapshot).encodeToByteArray()
        try {
            val out = atomic.startWrite()
            try {
                out.write(bytes)
                atomic.finishWrite(out)
            } catch (e: IOException) {
                atomic.failWrite(out)
                throw e
            }
        } catch (e: IOException) {
            Log.w(TAG, "widget cache write failed: ${e.message}")
        }
        Unit
    }

    private companion object {
        const val TAG = "WidgetSnapshotStore"
    }
}
