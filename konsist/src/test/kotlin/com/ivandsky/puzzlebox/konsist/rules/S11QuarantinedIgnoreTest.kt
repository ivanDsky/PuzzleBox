package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S11: every `@Ignore` in test sources carries the reason `quarantine #<issue> until <YYYY-MM-DD>`.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md); the quarantine policy: ADR-0023.
 */
class S11QuarantinedIgnoreTest {
    private val test = "games/sample/src/test/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `S11 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S11QuarantinedIgnore)
    }

    @Test
    fun `S11 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S11QuarantinedIgnore,
            "S11",
            12,
            setOf(
                Violation("$test/BadBare.kt", null, ""),
                Violation("$test/BadFreeReason.kt", null, ""),
                Violation("$test/BadQualified.kt", null, ""),
                Violation("$test/BadNoTicket.kt", null, ""),
                Violation("$test/BadImpossibleDate.kt", null, ""),
                Violation("$test/BadTrailingText.kt", null, ""),
                Violation("$test/BadConstant.kt", null, ""),
                Violation("$test/BadClassLevel.kt", null, ""),
                Violation("$test/BadKotlinTest.kt", null, ""),
                Violation("$test/BadSecondIgnore.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S11 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S11QuarantinedIgnore)
    }
}
