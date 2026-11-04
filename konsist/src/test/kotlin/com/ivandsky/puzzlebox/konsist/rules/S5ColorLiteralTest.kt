package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S5: production code outside the design-system package must not build `Color(<number>...)`; use a token.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S5ColorLiteralTest {
    private val dir = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `S5 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S5ColorLiteral)
    }

    @Test
    fun `S5 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S5ColorLiteral,
            "S5",
            8,
            setOf(
                Violation("$dir/Hex.kt", 2, ""),
                Violation("$dir/Named.kt", 2, ""),
                Violation("$dir/Qualified.kt", 1, ""),
                Violation("$dir/Triple.kt", 3, ""),
                Violation("$dir/Spaced.kt", 2, ""),
                Violation("$dir/Wildcard.kt", 1, ""),
            ),
        )
    }

    @Test
    fun `S5 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S5ColorLiteral)
    }
}
