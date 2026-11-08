package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's share decode is the friend's open path: the frozen v1 fixture and every size
 * the editor offers must keep decoding under it, while a size the editor does not offer — the
 * tutorial boards — is refused as an unknown variant.
 */
class ZipEditorShareDecodeTest {

    private fun fixture(): ByteArray =
        ZipEditorShareDecodeTest::class.java.getResourceAsStream("/editor/zip-share-v1.bin")!!.use { it.readBytes() }

    /** Tutorial bytes: version 1, board [n], no walls, no waypoints. */
    private fun tutorialBytes(n: Int): ByteArray = byteArrayOf(1, n.toByte(), 0, 0)

    private fun error(bytes: ByteArray): DecodeError = (ZipEditor.decodeShare(bytes) as DecodeResult.Err).error

    @Test
    fun `the frozen v1 fixture still decodes through the editor`() {
        val draft = (ZipEditor.decodeShare(fixture()) as DecodeResult.Ok).value

        assertEquals(5, draft.n)
    }

    @Test
    fun `every size the editor offers decodes back`() {
        for (variant in ZipEditor.variants) {
            val draft = ZipEditor.newDraft(variant)

            assertTrue(ZipEditor.decodeShare(ZipEditor.encodeShare(draft)) is DecodeResult.Ok)
        }
    }

    @Test
    fun `a tutorial-sized share payload is an unknown variant`() {
        for (n in 3..4) {
            assertEquals(DecodeError.UnknownVariant("zip/n$n"), error(tutorialBytes(n)))
        }
    }

    @Test
    fun `opening a tutorial-sized payload fails the friend's check`() {
        val result = SharedOpen.verify(ZipEditor, ZipEditor.shareFormatVersion, tutorialBytes(3), Budget.Unlimited)

        assertEquals(SharedOpenResult.FailedCheck, result)
    }
}
