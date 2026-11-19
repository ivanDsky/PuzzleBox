package com.ivandsky.puzzlebox.insights.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** The content Uri a shared recap file is handed out under: the app's FileProvider, authority `<applicationId>.share`. */
internal class RecapShareUris(private val context: Context) {

    fun of(file: File): Uri = FileProvider.getUriForFile(context, authority(context.packageName), file)

    companion object {

        /** The authority `app`'s manifest declares for the share FileProvider. */
        fun authority(applicationId: String): String = "$applicationId.share"
    }
}
