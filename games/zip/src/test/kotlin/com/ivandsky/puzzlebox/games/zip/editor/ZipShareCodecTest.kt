package com.ivandsky.puzzlebox.games.zip.editor

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Frozen share codec v1 bytes (ADR-0005): written once, never regenerated. */
class ZipShareCodecTest {

    private fun fixture(): ByteArray =
        ZipShareCodecTest::class.java.getResourceAsStream("/editor/zip-share-v1.bin")!!.use { it.readBytes() }

    @Test
    fun `the frozen fixture holds the pinned v1 bytes`() {
        val walls = listOf(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19)
        val expected = listOf(1, 5, walls.size) + walls.flatMap { listOf(it, 1) } + listOf(3, 0, 12, 24)

        assertArrayEquals(expected.map { it.toByte() }.toByteArray(), fixture())
    }
}
