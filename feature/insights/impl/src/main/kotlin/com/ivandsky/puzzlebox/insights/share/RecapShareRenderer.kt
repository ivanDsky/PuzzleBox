package com.ivandsky.puzzlebox.insights.share

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Writes a recap image where the share FileProvider can hand it out: a PNG in `share/` under [cacheRoot], the
 * app's cache directory. Only one recap image lives there: [render] removes the previous file first and [clear]
 * removes it without writing a new one, so a recap image never stays on the device past the next share or app start.
 */
internal class RecapShareRenderer(private val cacheRoot: File, private val dispatchers: DispatcherProvider) {

    private val directory: File get() = File(cacheRoot, DIRECTORY)

    /** Saves [image] as `<name>.png` and returns the file; throws [IOException] when it cannot be written. */
    suspend fun render(image: ImageBitmap, name: String): File = withContext(dispatchers.io) {
        removeFiles()
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        File(directory, "$name.png").also { file ->
            file.outputStream().use { out ->
                if (!image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) throw IOException("Cannot encode $file")
            }
        }
    }

    /** Removes every file a recap share left behind. */
    suspend fun clear() = withContext(dispatchers.io) { removeFiles() }

    private fun removeFiles() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        /** The directory `res/xml/share_paths.xml` exposes as a cache path. */
        const val DIRECTORY = "share"

        /** PNG ignores quality; the API asks for one. */
        const val PNG_QUALITY = 100
    }
}
