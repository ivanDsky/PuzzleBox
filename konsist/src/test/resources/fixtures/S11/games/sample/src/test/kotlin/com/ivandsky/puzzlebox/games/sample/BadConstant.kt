package com.ivandsky.puzzlebox.games.sample

import org.junit.Ignore
import org.junit.Test

private const val REASON = "quarantine #12 until 2099-12-31"

class BadConstant {
    @Ignore(REASON)
    @Test
    fun skipped() = Unit
}
