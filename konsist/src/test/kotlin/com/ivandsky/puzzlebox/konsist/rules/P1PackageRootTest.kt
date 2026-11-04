package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P1: every production file's package equals its module's package root or sits under it.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P1PackageRootTest {
    private val zip = "games/zip/src/main/kotlin"
    private val alpha = "core/alpha/src/main/kotlin"

    @Test
    fun `P1 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P1PackageRoot)
    }

    @Test
    fun `P1 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P1PackageRoot,
            "P1",
            7,
            setOf(
                Violation("$zip/BadSibling.kt", null, ""),
                Violation("$zip/BadPrefix.kt", null, ""),
                Violation("$zip/NoHeader.kt", null, ""),
                Violation("$alpha/Bad.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P1 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P1PackageRoot)
    }
}
