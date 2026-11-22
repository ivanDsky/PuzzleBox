package com.ivandsky.puzzlebox.games.sample

import org.junit.Ignore
import org.junit.Test

class BadSecondIgnore {
    @Ignore("quarantine #1495 until 2099-12-31")
    @Test
    fun quarantined() = Unit

    @Ignore
    @Test
    fun skipped() = Unit
}
