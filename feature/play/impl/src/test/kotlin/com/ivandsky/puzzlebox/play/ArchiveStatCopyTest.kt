package com.ivandsky.puzzlebox.play

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveStatCopyTest {

    @Test
    fun `one archived solve reads singular`() {
        assertEquals(
            "From the archive: 1 solve. They count toward your best time, not your streak.",
            archiveStatSentence(1),
        )
    }

    @Test
    fun `several archived solves read plural with a thousands separator`() {
        assertEquals(
            "From the archive: 12 solves. They count toward your best time, not your streak.",
            archiveStatSentence(12),
        )
        assertEquals(
            "From the archive: 1,234 solves. They count toward your best time, not your streak.",
            archiveStatSentence(1_234),
        )
    }
}
