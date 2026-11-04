package com.ivandsky.puzzlebox.konsist.core

import org.junit.Assert.fail

object RuleCheck {
    private const val ADD_A_GAME = "add-a-game"

    fun assertClean(rule: Rule) = assertClean(rule, SourceTree.real, Allowlist.load(rule.id))

    /** The real-tree check over any tree and allowlist; [assertClean] is this with the repo and the rule's file. */
    internal fun assertClean(rule: Rule, tree: SourceTree, allowlist: Allowlist) {
        val result = rule.check(tree)
        if (result.subjects == 0) fail("${rule.id}: scope empty")
        val verdict = judge(rule.id, result.violations, allowlist, scopePaths(rule, tree))
        if (!verdict.ok) fail(verdict.message())
    }

    /**
     * Exact subject count, then exact set comparison on path + value; hints are prose, so they never decide a match.
     * The count catches a scope that silently lost subjects while the violators stayed.
     */
    fun assertFixture(rule: Rule, fixtureId: String, expectedSubjects: Int, expected: Set<Violation>) {
        val result = rule.check(SourceTree.fixture(fixtureId))
        if (result.subjects == 0) fail("${rule.id}: scope empty in fixture $fixtureId")
        if (result.subjects != expectedSubjects) {
            fail("${rule.id}: fixture $fixtureId has ${result.subjects} subjects, expected $expectedSubjects")
        }
        val actualKeys = result.violations.map(::key).toSet()
        val expectedKeys = expected.map(::key).toSet()
        if (actualKeys == expectedKeys) return
        val missing = expectedKeys - actualKeys
        val unexpected = actualKeys - expectedKeys
        fail(
            buildString {
                append("${rule.id}: fixture $fixtureId differs from expected")
                missing.sortedBy { it.first }.forEach { append("\n  missing: ${show(it)}") }
                unexpected.sortedBy { it.first }.forEach { append("\n  unexpected: ${show(it)}") }
            },
        )
    }

    /** Zero subjects is fine here: a game has no P3, P4 or S5 subjects. */
    fun assertAddAGameClean(rule: Rule) {
        val violations = rule.check(SourceTree.fixture(ADD_A_GAME)).violations
        if (violations.isEmpty()) return
        fail(
            "${rule.id}: add-a-game fixture must have zero violations\n" +
                violations.sortedBy { it.path }.joinToString("\n") { "  ${it.path}${it.value?.let { v -> " ($v)" }.orEmpty()} — ${it.hint}" },
        )
    }

    internal fun scopePaths(rule: Rule, tree: SourceTree): Set<String> =
        rule.kinds.flatMap { tree.files(it) }.map { it.path }.toSet()

    private fun key(violation: Violation) = violation.path to violation.value

    private fun show(key: Pair<String, Int?>) = key.first + key.second?.let { " ($it)" }.orEmpty()
}
