package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPuzzleDocTest {

    @Test
    fun `a doc built without the community fields is link only with no difficulty`() {
        assertEquals(SharedPuzzleDoc.Visibility.LinkOnly, doc().visibility)
        assertNull(doc().difficulty)
    }

    @Test
    fun `docs that differ only in visibility are not equal`() {
        val public = doc().copy(visibility = SharedPuzzleDoc.Visibility.Public)

        assertNotEquals(doc(), public)
        assertNotEquals(doc().hashCode(), public.hashCode())
    }

    @Test
    fun `docs that differ only in difficulty are not equal`() {
        val hard = doc().copy(difficulty = Difficulty.Level.Hard)

        assertNotEquals(doc(), hard)
        assertNotEquals(hard, doc().copy(difficulty = Difficulty.Level.Expert))
        assertNotEquals(doc().hashCode(), hard.hashCode())
    }

    @Test
    fun `docs with the same community fields and payload bytes are equal`() {
        val one = doc().copy(visibility = SharedPuzzleDoc.Visibility.Public, difficulty = Difficulty.Level.Easy)
        val other = doc().copy(visibility = SharedPuzzleDoc.Visibility.Public, difficulty = Difficulty.Level.Easy)

        assertEquals(one, other)
        assertEquals(one.hashCode(), other.hashCode())
    }

    private fun doc() = SharedPuzzleDoc(
        code = checkNotNull(ShareCode.parse("K7Q2MX")),
        formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
        gameId = "queens",
        variant = "classic",
        shareCodecVersion = 1,
        payload = byteArrayOf(1, 2, 3, 4),
        title = "Corner queens",
        authorName = "Ivan",
        note = "Start in the corner",
        authorUid = "author-uid",
        authorTimeMs = 61_000,
        authorHints = 0,
        createdAtMs = 1_762_000_000_000,
        unpublished = false,
    )
}
