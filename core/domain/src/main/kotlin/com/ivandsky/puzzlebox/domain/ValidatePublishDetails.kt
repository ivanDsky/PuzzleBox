package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.game.editor.PublishBlock

/**
 * The publish details' caps (spec A25): what keeps an author name or note from publishing.
 * Lengths count code points after trimming, as the backend's rules count a string, so an
 * emoji is one character.
 */
class ValidatePublishDetails {
    /** AUTHOR_TOO_LONG when the trimmed authorName exceeds Draft.AUTHOR_MAX code points; NOTE_TOO_LONG likewise for note and Draft.NOTE_MAX. */
    operator fun invoke(authorName: String, note: String): List<PublishBlock> = buildList {
        if (authorName.trimmedLength() > Draft.AUTHOR_MAX) add(PublishBlock(PublishBlock.AUTHOR_TOO_LONG))
        if (note.trimmedLength() > Draft.NOTE_MAX) add(PublishBlock(PublishBlock.NOTE_TOO_LONG))
    }

    private fun String.trimmedLength(): Int = trim().let { it.codePointCount(0, it.length) }

    companion object {
        /** The details' block ids (ReadinessCopyTest). */
        val blockIds: Set<String> = setOf(PublishBlock.AUTHOR_TOO_LONG, PublishBlock.NOTE_TOO_LONG)
    }
}
