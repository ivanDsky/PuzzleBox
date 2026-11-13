package com.ivandsky.puzzlebox.games.strandsui

import org.junit.Assert.assertFalse
import org.junit.Test

/** F21: a Strands replay can show no error, so its board tells the replay pages to hide Show errors. */
class StrandsReplayErrorsTest {
    @Test
    fun `the Strands replay board has no errors to show`() {
        assertFalse(StrandsPlayUi().replayBoard.hasErrors)
    }
}
