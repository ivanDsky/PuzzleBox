package com.ivandsky.puzzlebox.tools.puzzlecli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GenerateDrillsOptionsTest {

    private fun rejected(vararg args: String): String =
        try {
            GenerateDrillsOptions.parse(args.toList())
            fail("expected a rejection of ${args.toList()}")
            ""
        } catch (bad: IllegalArgumentException) {
            bad.message.orEmpty()
        }

    @Test
    fun `only the two directories are required and the rest default`() {
        val options = GenerateDrillsOptions.parse(listOf("--index", "idx", "--out", "o"))
        assertEquals("idx", options.index)
        assertEquals("o", options.out)
        assertEquals(GenerateDrillsOptions.DEFAULT_SEED, options.seed)
        assertEquals(5, options.perTechnique)
        assertEquals(GenerateDrillsOptions.DEFAULT_MAX_ATTEMPTS, options.maxAttempts)
    }

    @Test
    fun `every flag is read in any order`() {
        val options = GenerateDrillsOptions.parse(
            listOf("--max-attempts", "7", "--seed", "-42", "--out", "o", "--per-technique", "3", "--index", "idx"),
        )
        assertEquals(7, options.maxAttempts)
        assertEquals(-42L, options.seed)
        assertEquals(3, options.perTechnique)
        assertEquals("o", options.out)
        assertEquals("idx", options.index)
    }

    @Test
    fun `a missing directory is named`() {
        assertTrue(rejected("--out", "o").contains("--index"))
        assertTrue(rejected("--index", "idx").contains("--out"))
    }

    @Test
    fun `an unknown, repeated or valueless flag is refused`() {
        assertTrue(rejected("--index", "idx", "--out", "o", "--verbose").contains("--verbose"))
        assertTrue(rejected("--index", "idx", "--index", "again", "--out", "o").contains("twice"))
        assertTrue(rejected("--index", "idx", "--out").contains("--out needs a value"))
        assertTrue(rejected("stray").contains("stray"))
    }

    @Test
    fun `a bad number is refused`() {
        assertTrue(rejected("--index", "i", "--out", "o", "--seed", "x").contains("--seed"))
        assertTrue(rejected("--index", "i", "--out", "o", "--per-technique", "0").contains("--per-technique"))
        assertTrue(rejected("--index", "i", "--out", "o", "--per-technique", "6").contains("--per-technique"))
        assertTrue(rejected("--index", "i", "--out", "o", "--per-technique", "many").contains("--per-technique"))
        assertTrue(rejected("--index", "i", "--out", "o", "--max-attempts", "0").contains("--max-attempts"))
    }
}
