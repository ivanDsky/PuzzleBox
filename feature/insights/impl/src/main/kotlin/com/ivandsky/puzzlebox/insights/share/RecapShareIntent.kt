package com.ivandsky.puzzlebox.insights.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/** The chooser that offers a recap's image and text to other apps. */
internal object RecapShareIntent {

    const val MIME_TYPE = "image/png"

    /**
     * An ACTION_SEND for the image at [uri] with [text], wrapped in a chooser. The receiving app gets read access to
     * the one file through the grant on the intent and its clip; nothing else of ours is exposed.
     */
    fun create(uri: Uri, text: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
