package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's share decode is the friend's open path: the frozen v1 fixture and every size
 * the editor offers must keep decoding under it while the variant gate tightens.
 */
class ZipEditorShareDecodeTest {

    private fun fixture(): ByteArray =
        ZipEditorShareDecodeTest::class.java.getResourceAsStream("/editor/zip-share-v1.bin")!!.use { it.readBytes() }

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
}
