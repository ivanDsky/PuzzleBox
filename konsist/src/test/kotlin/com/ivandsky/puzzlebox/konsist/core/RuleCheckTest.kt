package com.ivandsky.puzzlebox.konsist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuleCheckTest {
    private class StubRule(
        override val id: String,
        override val kinds: Set<SourceSetKind> = setOf(SourceSetKind.PRODUCTION),
        private val subjects: (SourceTree) -> Int = { 1 },
        private val violations: (SourceTree) -> List<Violation> = { emptyList() },
    ) : Rule {
        override fun check(tree: SourceTree) = RuleResult(subjects(tree), violations(tree))
    }

    private fun failure(block: () -> Unit): String {
        try {
            block()
        } catch (e: AssertionError) {
            return e.message.orEmpty()
        }
        fail("expected an AssertionError")
        error("unreachable")
    }

    @Test
    fun assertCleanFailsOnAnEmptyScope() {
        val message = failure { RuleCheck.assertClean(StubRule("ZZ", subjects = { 0 })) }
        assertEquals("ZZ: scope empty", message)
    }

    @Test
    fun assertCleanPassesWithSubjectsAndNoViolations() {
        RuleCheck.assertClean(StubRule("ZZ"))
    }

    @Test
    fun assertCleanFailsWithTheVerdictMessageVerbatim() {
        val violation = Violation("core/model/src/main/kotlin/Model.kt", null, "too big")
        val message = failure { RuleCheck.assertClean(StubRule("ZZ", violations = { listOf(violation) })) }
        val expected = judge("ZZ", listOf(violation), Allowlist.parse("ZZ", ""), emptySet()).message()
        assertEquals(expected, message)
        assertTrue(message, message.startsWith("ZZ: 1 finding(s)"))
    }

    @Test
    fun assertFixtureMatchesOnPathAndValueIgnoringHint() {
        val rule = StubRule(
            "ZZ",
            violations = { listOf(Violation("games/a/src/main/kotlin/A.kt", 3, "prose one")) },
        )
        RuleCheck.assertFixture(rule, "_tree", 1, setOf(Violation("games/a/src/main/kotlin/A.kt", 3, "other prose")))
    }

    @Test
    fun assertFixtureListsMissingAndUnexpected() {
        val rule = StubRule(
            "ZZ",
            violations = {
                listOf(
                    Violation("games/a/src/main/kotlin/A.kt", 3, "h"),
                    Violation("app/src/main/kotlin/App.kt", null, "h"),
                )
            },
        )
        val expected = setOf(
            Violation("games/a/src/main/kotlin/A.kt", 4, "h"),
            Violation("core/model/src/main/kotlin/Model.kt", null, "h"),
        )
        val message = failure { RuleCheck.assertFixture(rule, "_tree", 1, expected) }
        assertTrue(message, message.startsWith("ZZ: fixture _tree"))
        assertTrue(message, message.contains("missing") && message.contains("games/a/src/main/kotlin/A.kt (4)"))
        assertTrue(message, message.contains("core/model/src/main/kotlin/Model.kt"))
        assertTrue(message, message.contains("unexpected") && message.contains("games/a/src/main/kotlin/A.kt (3)"))
        assertTrue(message, message.contains("app/src/main/kotlin/App.kt"))
    }

    @Test
    fun assertFixtureFailsOnZeroSubjects() {
        val message = failure { RuleCheck.assertFixture(StubRule("ZZ", subjects = { 0 }), "_tree", 0, emptySet()) }
        assertTrue(message, message.contains("ZZ: scope empty"))
    }

    @Test
    fun assertFixtureScansTheNamedFixture() {
        val seen = mutableListOf<String>()
        val rule = StubRule("ZZ", subjects = { tree -> seen += tree.root.name; 1 })
        RuleCheck.assertFixture(rule, "_tree", 1, emptySet())
        assertEquals(listOf("_tree"), seen)
    }

    @Test
    fun assertFixtureFailsWhenTheSubjectCountDiffers() {
        val message = failure { RuleCheck.assertFixture(StubRule("ZZ", subjects = { 4 }), "_tree", 5, emptySet()) }
        assertEquals("ZZ: fixture _tree has 4 subjects, expected 5", message)
    }

    @Test
    fun assertAddAGameCleanPassesWithZeroSubjects() {
        RuleCheck.assertAddAGameClean(StubRule("ZZ", subjects = { 0 }))
    }

    @Test
    fun assertAddAGameCleanScansTheAddAGameFixtureAndFailsOnAViolation() {
        val seen = mutableListOf<String>()
        RuleCheck.assertAddAGameClean(StubRule("ZZ", subjects = { tree -> seen += tree.root.name; 1 }))
        assertEquals(listOf("add-a-game"), seen)

        val rule = StubRule("ZZ", violations = { listOf(Violation("games/sample/src/main/kotlin/x.kt", null, "h")) })
        val message = failure { RuleCheck.assertAddAGameClean(rule) }
        assertTrue(message, message.contains("games/sample/src/main/kotlin/x.kt"))
    }

    @Test
    fun scopePathsHonourKinds() {
        val tree = SourceTree.fixture("_tree")
        val test = "core/model/src/test/kotlin/ModelTest.kt"
        val main = "core/model/src/main/kotlin/Model.kt"

        val production = RuleCheck.scopePaths(StubRule("ZZ"), tree)
        assertTrue(main in production && test !in production)

        val both = RuleCheck.scopePaths(StubRule("ZZ", kinds = SourceSetKind.values().toSet()), tree)
        assertTrue(main in both && test in both)

        val testOnly = RuleCheck.scopePaths(StubRule("ZZ", kinds = setOf(SourceSetKind.TEST)), tree)
        assertEquals(setOf(test), testOnly)
    }

    @Test
    fun anAllowlistedTestPathIsStaleForAProductionRule() {
        val tree = SourceTree.fixture("_tree")
        val allowlist = Allowlist.parse("ZZ", "core/model/src/test/kotlin/ModelTest.kt\n")
        val production = RuleCheck.verdict(StubRule("ZZ"), tree, allowlist)
        assertTrue(production.message(), production.message().contains("path not in scope"))

        val both = StubRule("ZZ", kinds = SourceSetKind.values().toSet(), violations = {
            listOf(Violation("core/model/src/test/kotlin/ModelTest.kt", null, "h"))
        })
        assertTrue(RuleCheck.verdict(both, tree, allowlist).ok)
    }

    @Test
    fun theDefaultKindsAreProductionOnly() {
        val rule = object : Rule {
            override val id = "ZZ"
            override fun check(tree: SourceTree) = RuleResult(1, emptyList())
        }
        assertEquals(setOf(SourceSetKind.PRODUCTION), rule.kinds)
    }
}
