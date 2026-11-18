package com.ivandsky.puzzlebox.games.sample

import org.junit.Test

class BadQualified {
    @org.junit.Ignore("quarantine")
    @Test
    fun skipped() = Unit
}
