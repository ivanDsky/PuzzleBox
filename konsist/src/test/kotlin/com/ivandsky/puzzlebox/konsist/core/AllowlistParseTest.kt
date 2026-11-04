package com.ivandsky.puzzlebox.konsist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowlistParseTest {
    private fun parse(text: String) = Allowlist.parse("R", text)

    @Test
    fun pathOnlyAndCeilingEntries() {
        val a = parse("a/B.kt\nc/D.kt 120\n")
        assertEquals(listOf(AllowlistEntry("a/B.kt", null, 1), AllowlistEntry("c/D.kt", 120, 2)), a.entries)
        assertTrue(a.problems.isEmpty())
    }

    @Test
    fun commentsAndBlanksIgnoredWithLineNumbersKept() {
        val a = parse("# head\n\na/B.kt 5 # why\n   \nc/D.kt # tail\n")
        assertEquals(listOf(AllowlistEntry("a/B.kt", 5, 3), AllowlistEntry("c/D.kt", null, 5)), a.entries)
        assertTrue(a.problems.isEmpty())
    }

    @Test
    fun malformedFormsAreReturnedNotThrown() {
        val a = parse("a b c\nx.kt abc\ny.kt -1\n/abs/z.kt\na\\b.kt\n../up.kt\nq/../r.kt\nok.kt\n")
        assertEquals(listOf(AllowlistEntry("ok.kt", null, 8)), a.entries)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), a.problems.map { it.line })
        assertTrue(a.problems.all { it is Finding.Malformed })
    }

    @Test
    fun duplicateNamesBothLines() {
        val a = parse("a.kt\nb.kt\na.kt 3\n")
        assertEquals(listOf(Finding.Duplicate(3, 1, "a.kt")), a.problems)
        assertEquals(listOf("a.kt", "b.kt"), a.entries.map { it.path })
        assertTrue(a.problems[0].text.contains("line 1"))
    }

    @Test
    fun missingResourceIsEmpty() {
        val a = Allowlist.load("NO-SUCH-RULE")
        assertTrue(a.entries.isEmpty())
        assertTrue(a.problems.isEmpty())
    }
}
