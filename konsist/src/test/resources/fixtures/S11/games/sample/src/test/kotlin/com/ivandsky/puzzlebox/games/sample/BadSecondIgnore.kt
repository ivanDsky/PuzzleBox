package com.ivandsky.puzzlebox.games.sample

import org.junit.Ignore
import org.junit.Test

class BadSecondIgnore {
    @Ignore("quarantine #1495 until 2026-11-20")
    @Test
    fun quarantined() = Unit

    @Ignore
    @Test
    fun skipped() = Unit
}
