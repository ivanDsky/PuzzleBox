package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Allowlist
import com.ivandsky.puzzlebox.konsist.core.Finding
import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.ivandsky.puzzlebox.konsist.core.judge
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P14: a production file of `:core:domain` (outside `di` packages) with a non-private top-level declaration is not
 * used by exactly one feature. Consumers are the production files of every other module and the test files of every
 * module but `:core:domain` and `:app`; `:feature:<x>:api` and `:feature:<x>:impl` are one feature, and a domain file
 * that names another passes its consumers on to it.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P14DomainSingleConsumerTest {
    private val dir = "core/domain/src/main/kotlin/com/ivandsky/puzzlebox/domain"

    private val expected = setOf(
        Violation("$dir/SingleFeature.kt", null, ""),
        Violation("$dir/HelperOfSingle.kt", null, ""),
        Violation("$dir/AppTestToo.kt", null, ""),
        Violation("$dir/InlineUse.kt", null, ""),
        Violation("$dir/NestedImport.kt", null, ""),
        Violation("$dir/ApiAndImpl.kt", null, ""),
        Violation("$dir/CommentOnly.kt", null, ""),
        // GetViaB is a composite only feature b uses; LeafThing, which feature a uses directly, stays clean.
        Violation("$dir/CompositeOfB.kt", null, ""),
        Violation("$dir/ranks/RankExtensions.kt", null, ""),
        Violation("$dir/stars/StarTarget.kt", null, ""),
    )

    @Test
    fun `P14 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P14DomainSingleConsumer)
    }

    @Test
    fun `P14 fixture yields exact violations`() {
        RuleCheck.assertFixture(P14DomainSingleConsumer, "P14", 17, expected)
    }

    @Test
    fun `P14 judge reports a new violator and a stale entry`() {
        val tree = SourceTree.fixture("P14")
        val violations = P14DomainSingleConsumer.check(tree).violations
        // The allowlist misses one violator and names a clean file: the first is new, the second stale.
        val frozen = expected.map { it.path }.filterNot { it.endsWith("/SingleFeature.kt") } + "$dir/TwoFeatures.kt"
        val allowlist = Allowlist.parse("P14", frozen.joinToString("\n"))
        val verdict = judge("P14", violations, allowlist, RuleCheck.scopePaths(P14DomainSingleConsumer, tree))
        val found = verdict.findings.map {
            when (it) {
                is Finding.New -> "new ${it.violation.path}"
                is Finding.Stale -> "stale ${it.entry.path}"
                else -> "other ${it.text}"
            }
        }.toSet()
        assertEquals(setOf("new $dir/SingleFeature.kt", "stale $dir/TwoFeatures.kt"), found)
    }

    @Test
    fun `P14 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P14DomainSingleConsumer)
    }
}
