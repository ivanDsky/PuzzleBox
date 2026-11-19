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
 * app's cache directory. Only one recap image lives there: [render] removes the previous recap file first and [clear]
 * removes it without writing a new one, so a recap image never stays on the device past the next share or app start.
 */
internal class RecapShareRenderer(private val cacheRoot: File, private val dispatchers: DispatcherProvider) {

    private val directory: File get() = File(cacheRoot, DIRECTORY)

    /** Saves [image] as `recap-<name>.png` and returns the file; throws [IOException] when it cannot be written. */
    suspend fun render(image: ImageBitmap, name: String): File = withContext(dispatchers.io) {
        removeFiles()
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        File(directory, "$FILE_PREFIX$name$FILE_SUFFIX").also { file ->
            file.outputStream().use { out ->
                if (!image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) throw IOException("Cannot encode $file")
            }
        }
    }

    /** Removes every file a recap share left behind. */
    suspend fun clear() = withContext(dispatchers.io) { removeFiles() }

    /** Only this feature's files: other share flows may keep their own in the same directory. */
    private fun removeFiles() {
        directory.listFiles { file -> file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX) }?.forEach { it.delete() }
    }

    private companion object {
        /** The directory `res/xml/share_paths.xml` exposes as a cache path. */
        const val DIRECTORY = "share"

        /** Every file this renderer writes is named `recap-<name>.png`; nothing else in the directory is its to delete. */
        const val FILE_PREFIX = "recap-"
        const val FILE_SUFFIX = ".png"

        /** PNG ignores quality; the API asks for one. */
        const val PNG_QUALITY = 100
    }
}
