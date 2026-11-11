package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P10: the LinkedIn account path is GET-only, with no logging interceptor and no cookie jar.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P10LinkedInAccountGetOnlyTest {
    private val dir = "core/network/src/main/kotlin/com/ivandsky/puzzlebox/network/linkedin"

    @Test
    fun `P10 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P10LinkedInAccountGetOnly)
    }

    @Test
    fun `P10 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P10LinkedInAccountGetOnly,
            "P10",
            2,
            setOf(
                Violation("$dir/LinkedInAccountApi.kt", null, ""),
                Violation("$dir/LinkedInAccountNetwork.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P10 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P10LinkedInAccountGetOnly)
    }
}
