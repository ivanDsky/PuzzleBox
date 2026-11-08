package com.ivandsky.puzzlebox.app.nav

import android.content.Intent

/**
 * The text a `SEND` share offers, or null when the share is not readable text: only
 * [Intent.ACTION_SEND] with a `text/plain` type and a non-blank [extraText] counts.
 * An [Intent.EXTRA_STREAM] share (an image, a file) carries no readable text, so its
 * type is not `text/plain` and it never matches.
 */
internal object SharedText {

    /** [extraText] as readable text when [action] is a plain-text [Intent.ACTION_SEND]; null otherwise. */
    fun of(action: String?, type: String?, extraText: CharSequence?): String? {
        if (action != Intent.ACTION_SEND) return null
        if (type != "text/plain") return null
        val text = extraText?.toString() ?: return null
        return text.takeIf { it.isNotBlank() }
    }
}
