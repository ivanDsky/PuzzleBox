package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S0: no `@Suppress` aimed at a suite rule (`konsist.*` or a rule ID such as `S1`, `P5`) in production or test sources.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S0NoSuppressTest {
    private val main = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"
    private val test = "games/sample/src/test/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `S0 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S0NoSuppress)
    }

    @Test
    fun `S0 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S0NoSuppress,
            "S0",
            setOf(
                Violation("$main/BadQualified.kt", null, ""),
                Violation("$main/BadBareId.kt", null, ""),
                Violation("$main/BadFileLevel.kt", null, ""),
                Violation("$main/BadNamed.kt", null, ""),
                Violation("$main/BadKotlinQualified.kt", null, ""),
                Violation("$main/BadMixed.kt", null, ""),
                Violation("$main/BadArrayOf.kt", null, ""),
                Violation("$test/BadInTest.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S0 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S0NoSuppress)
    }
}
