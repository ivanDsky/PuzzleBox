package com.ivandsky.puzzlebox.profile.whatsnew

import android.content.Context

/** Reads the bundled `whats_new.txt` and parses it; a missing file or a format error propagates to the caller. */
class AssetWhatsNewSource(private val context: Context) : WhatsNewSource {

    override fun releases(): List<WhatsNewRelease> {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        return WhatsNewParser.parse(text)
    }

    private companion object {
        const val ASSET = "whats_new.txt"
    }
}
