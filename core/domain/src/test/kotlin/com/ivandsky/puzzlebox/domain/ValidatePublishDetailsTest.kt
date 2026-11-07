package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Publish details (spec A25): the author name holds up to 40 characters and the note up to 140,
 * counted after trimming, in code points, so an emoji is one character, as the backend counts it.
 */
class ValidatePublishDetailsTest {

    private val validate = ValidatePublishDetails()

    private fun blocks(authorName: String = "", note: String = ""): List<String> = validate(authorName, note).map { it.id }

    @Test
    fun `empty details are fine`() {
        assertEquals(emptyList<String>(), blocks())
    }

    @Test
    fun `an author name of 40 passes and 41 is too long`() {
        assertEquals(emptyList<String>(), blocks(authorName = "a".repeat(Draft.AUTHOR_MAX)))
        assertEquals(listOf(PublishBlock.AUTHOR_TOO_LONG), blocks(authorName = "a".repeat(Draft.AUTHOR_MAX + 1)))
    }

    @Test
    fun `a note of 140 passes and 141 is too long`() {
        assertEquals(emptyList<String>(), blocks(note = "n".repeat(Draft.NOTE_MAX)))
        assertEquals(listOf(PublishBlock.NOTE_TOO_LONG), blocks(note = "n".repeat(Draft.NOTE_MAX + 1)))
    }

    @Test
    fun `surrounding whitespace does not count`() {
        assertEquals(emptyList<String>(), blocks(authorName = "  ${"a".repeat(Draft.AUTHOR_MAX)}\n", note = " ${"n".repeat(Draft.NOTE_MAX)} "))
    }

    @Test
    fun `an emoji counts as one character, not two UTF-16 units`() {
        val emoji = "\uD83E\uDDE9" // 🧩, a surrogate pair
        assertEquals(emptyList<String>(), blocks(authorName = emoji.repeat(Draft.AUTHOR_MAX), note = emoji.repeat(Draft.NOTE_MAX)))
        assertEquals(
            listOf(PublishBlock.AUTHOR_TOO_LONG, PublishBlock.NOTE_TOO_LONG),
            blocks(authorName = emoji.repeat(Draft.AUTHOR_MAX + 1), note = emoji.repeat(Draft.NOTE_MAX + 1)),
        )
    }

    @Test
    fun `the block ids are the two details ids`() {
        assertEquals(setOf(PublishBlock.AUTHOR_TOO_LONG, PublishBlock.NOTE_TOO_LONG), ValidatePublishDetails.blockIds)
    }
}
