package com.ivandsky.puzzlebox.games.sample

import org.junit.Ignore
import org.junit.Test

// @Ignore in a comment is not an annotation
class GoodQuarantine {
    @Ignore("quarantine #1495 until 2099-12-31")
    @Test
    fun positional() = Unit

    @org.junit.Ignore(value = "quarantine #12 until 2099-12-31")
    @Test
    fun namedAndQualified() = Unit

    @Test
    fun mention() {
        val text = "@Ignore"
    }
}
