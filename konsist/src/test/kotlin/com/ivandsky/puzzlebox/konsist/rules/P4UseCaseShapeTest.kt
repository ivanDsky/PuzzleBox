package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P4: a public class in `:core:domain` (outside `di` packages) starts with a verb from the list, followed by an
 * uppercase letter, and has exactly one public `operator fun invoke`; `suspend` is allowed, overloads are not.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P4UseCaseShapeTest {
    private val dir = "core/domain/src/main/kotlin/com/ivandsky/puzzlebox/domain"

    @Test
    fun `P4 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P4UseCaseShape())
    }

    @Test
    fun `P4 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P4UseCaseShape(),
            "P4",
            setOf(
                Violation("$dir/ThingFetcher.kt", null, ""),
                Violation("$dir/Getter.kt", null, ""),
                Violation("$dir/LoadNothing.kt", null, ""),
                Violation("$dir/SaveTwice.kt", null, ""),
                Violation("$dir/DeleteLoose.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P4 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P4UseCaseShape())
    }
}
