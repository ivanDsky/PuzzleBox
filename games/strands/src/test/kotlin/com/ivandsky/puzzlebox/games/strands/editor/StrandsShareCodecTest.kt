package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.game.editor.canShare
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share codec v1 (ADR-0020): letters, clue, word texts and the spangram index, never paths, so
 * the recipient re-derives the tiling ([SharedOpen]); unknown versions are typed (ADR-0005).
 */
class StrandsShareCodecTest {

    private fun fixture(): ByteArray =
        StrandsShareCodecTest::class.java.getResourceAsStream("/editor/strands-share-v1.bin")!!.use { it.readBytes() }

    private fun decodeOk(bytes: ByteArray): StrandsDraft = (StrandsEditor.decodeShare(bytes) as DecodeResult.Ok).value

    private fun error(bytes: ByteArray): DecodeError = (StrandsEditor.decodeShare(bytes) as DecodeResult.Err).error

    private fun assertMalformed(bytes: ByteArray) {
        val error = error(bytes)
        assertTrue("expected Malformed, got $error", error is DecodeError.Malformed)
    }

    private fun word(row: Int) = "${'A' + row}".repeat(StrandsDraft.COLS)

    private val letters = List(StrandsDraft.ROWS) { word(it) }.joinToString("")

    /** What `strands-share-v1.bin` holds: row-striped letters, one word per row, row 1 the spangram. */
    private val shared = StrandsDraft(
        letters = letters,
        clue = "Rows",
        paths = List(StrandsDraft.ROWS) { StrandsDraftPath(emptyList(), word(it)) },
        spangram = 1,
    )

    /** The sender's draft: the same board with its drawn paths. */
    private val drawn = shared.copy(
        paths = shared.paths.mapIndexed { row, path ->
            path.copy(cells = (0 until StrandsDraft.COLS).map { row * StrandsDraft.COLS + it })
        },
    )

    @Test
    fun `frozen v1 fixture decodes with empty paths`() {
        assertEquals(shared, decodeOk(fixture()))
    }

    @Test
    fun `encoding the drawn board reproduces the frozen bytes`() {
        assertArrayEquals(fixture(), StrandsEditor.encodeShare(drawn))
    }

    @Test
    fun `the editor advertises share format version 1`() {
        assertEquals(1, StrandsEditor.shareFormatVersion)
    }

    @Test
    fun `a bumped version byte is an unsupported version`() {
        val bumped = fixture().also { it[0] = 2 }

        assertEquals(DecodeError.UnsupportedVersion(found = 2, maxKnown = 1), error(bumped))
    }

    @Test
    fun `a version byte of zero is malformed`() {
        assertMalformed(fixture().also { it[0] = 0 })
    }

    @Test
    fun `empty and truncated payloads are malformed`() {
        val bytes = fixture()

        assertMalformed(ByteArray(0))
        assertMalformed(bytes.copyOf(1))
        assertMalformed(bytes.copyOf(bytes.size - 1))
    }

    @Test
    fun `trailing bytes are malformed`() {
        assertMalformed(fixture() + byteArrayOf(0))
    }

    @Test
    fun `a spangram index outside the words is malformed`() {
        assertMalformed(fixture().also { it[it.lastIndex] = 20 })
    }

    @Test
    fun `an unnamed path is malformed`() {
        val unnamed = drawn.copy(paths = drawn.paths.mapIndexed { i, p -> if (i == 3) p.copy(word = null) else p })

        assertMalformed(StrandsEditor.encodeShare(unnamed))
    }

    @Test
    fun `a Strands draft can share and encodes its current board`() {
        val draft = EditorDraft(StrandsEditor, DraftState(drawn))

        assertTrue(draft.canShare)
        assertArrayEquals(fixture(), draft.encodeShare())
    }

    @Test
    fun `the frozen fixture re-verifies into a puzzle with the baked valid words`() {
        val result = SharedOpen.verify(StrandsEditor, StrandsEditor.shareFormatVersion, fixture(), Budget.Unlimited)

        val verified = result as SharedOpenResult.Verified
        val puzzle = (StrandsCodec.decode(verified.puzzle.payload) as DecodeResult.Ok).value
        assertEquals(StrandsDefinition.id, verified.puzzle.gameId)
        assertEquals(StrandsDefinition.VARIANT, verified.variant)
        assertEquals(letters, puzzle.letters)
        assertEquals(word(1), puzzle.spangram.text)
        assertEquals(StrandsValidWords.of(letters, shared.paths.mapTo(HashSet()) { it.word!! }), puzzle.validWords)
    }

    @Test
    fun `a newer share version on the fixture needs an update`() {
        val result = SharedOpen.verify(StrandsEditor, StrandsEditor.shareFormatVersion, fixture().also { it[0] = 2 }, Budget.Unlimited)

        assertEquals(SharedOpenResult.NeedsUpdate(found = 2, maxKnown = 1), result)
    }
}
