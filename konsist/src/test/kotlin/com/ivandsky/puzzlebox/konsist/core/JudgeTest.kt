package com.ivandsky.puzzlebox.konsist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeTest {
    private val header = "S1: %d finding(s) — see docs/adr/0009-in-module-structure-rules.md#s1"

    private fun run(violations: List<Violation>, allowlist: String, scope: Set<String>) =
        judge("S1", violations, Allowlist.parse("S1", allowlist), scope)

    private fun v(path: String, value: Int? = null) = Violation(path, value, "split it")

    @Test
    fun newGrownAndSlack() {
        val verdict = run(
            listOf(v("a/New.kt"), v("a/Big.kt", 12), v("a/Small.kt", 7)),
            "a/Big.kt 10\na/Small.kt 9\n",
            setOf("a/New.kt", "a/Big.kt", "a/Small.kt"),
        )
        assertEquals(
            listOf(
                header.format(3),
                "  a/Big.kt value 12 vs ceiling 10 — a/Big.kt grew to 12 past ceiling 10 (split it); edit allowlists/S1.txt line 1",
                "  a/New.kt — new violation: a/New.kt (split it); fix the code (split it)",
                "  a/Small.kt value 7 vs ceiling 9 — a/Small.kt shrank to 7; lower ceiling 9; edit allowlists/S1.txt line 2",
            ).joinToString("\n"),
            verdict.message(),
        )
    }

    @Test
    fun staleNoLongerViolates() {
        val verdict = run(emptyList(), "a/Gone.kt\n", setOf("a/Gone.kt"))
        assertEquals(
            header.format(1) + "\n  a/Gone.kt — a/Gone.kt is stale: no longer violates; edit allowlists/S1.txt line 1",
            verdict.message(),
        )
    }

    @Test
    fun newCountViolationShowsValueAndNoCeiling() {
        val verdict = run(listOf(v("a/Wide.kt", 13)), "", setOf("a/Wide.kt"))
        assertEquals(
            header.format(1) +
                "\n  a/Wide.kt value 13 vs ceiling none — new violation: a/Wide.kt at 13 (split it); fix the code (split it)",
            verdict.message(),
        )
    }

    @Test
    fun staleForDeletedRenamedAndWrongRulePaths() {
        val verdict = run(
            emptyList(),
            "a/Deleted.kt\na/OldName.kt\nother/Wrong.kt\n",
            setOf("a/NewName.kt"),
        )
        assertEquals(
            listOf(
                header.format(3),
                "  a/Deleted.kt — a/Deleted.kt is stale: path not in scope; edit allowlists/S1.txt line 1",
                "  a/OldName.kt — a/OldName.kt is stale: path not in scope; edit allowlists/S1.txt line 2",
                "  other/Wrong.kt — other/Wrong.kt is stale: path not in scope; edit allowlists/S1.txt line 3",
            ).joinToString("\n"),
            verdict.message(),
        )
    }

    @Test
    fun ceilingMismatch() {
        val verdict = run(
            listOf(v("a/Presence.kt"), v("a/Counted.kt", 5)),
            "a/Presence.kt 3\na/Counted.kt\n",
            setOf("a/Presence.kt", "a/Counted.kt"),
        )
        assertEquals(
            listOf(
                header.format(2),
                "  a/Counted.kt — malformed entry 'a/Counted.kt': ceiling rule entry needs a ceiling; edit allowlists/S1.txt line 2",
                "  a/Presence.kt — malformed entry 'a/Presence.kt 3': presence rule entry must not have a ceiling; edit allowlists/S1.txt line 1",
            ).joinToString("\n"),
            verdict.message(),
        )
    }

    @Test
    fun foldsMalformedAndDuplicate() {
        val verdict = run(
            listOf(v("a/X.kt")),
            "a/X.kt\n/abs/Y.kt\na/X.kt\n",
            setOf("a/X.kt"),
        )
        assertEquals(
            listOf(
                header.format(2),
                "  /abs/Y.kt — malformed entry '/abs/Y.kt': absolute path; edit allowlists/S1.txt line 2",
                "  a/X.kt — duplicate entry a/X.kt (first on line 1); edit allowlists/S1.txt line 3",
            ).joinToString("\n"),
            verdict.message(),
        )
    }

    @Test
    fun fullyMatchedAllowlistIsOk() {
        val verdict = run(
            listOf(v("a/X.kt"), v("a/Y.kt", 4)),
            "a/X.kt\na/Y.kt 4\n",
            setOf("a/X.kt", "a/Y.kt", "a/Clean.kt"),
        )
        assertTrue(verdict.ok)
        assertEquals(header.format(0), verdict.message())
    }
}
