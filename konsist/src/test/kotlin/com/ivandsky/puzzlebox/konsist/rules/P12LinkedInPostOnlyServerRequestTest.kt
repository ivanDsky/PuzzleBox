package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P12: the LinkedIn post API has one POST, the `updateGameState` server request, and nothing in the package builds a write by hand.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P12LinkedInPostOnlyServerRequestTest {
    private val dir = "core/network/src/main/kotlin/com/ivandsky/puzzlebox/network/linkedin"

    @Test
    fun `P12 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P12LinkedInPostOnlyServerRequest)
    }

    @Test
    fun `P12 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P12LinkedInPostOnlyServerRequest,
            "P12",
            9,
            setOf(
                Violation("$dir/wrongpath/LinkedInPostApi.kt", null, ""),
                Violation("$dir/twopost/LinkedInPostApi.kt", null, ""),
                Violation("$dir/HandBuiltPost.kt", null, ""),
                Violation("$dir/HandBuiltPut.kt", null, ""),
                Violation("$dir/HandBuiltPatch.kt", null, ""),
                Violation("$dir/HandBuiltDelete.kt", null, ""),
                Violation("$dir/HandBuiltMethod.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P12 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P12LinkedInPostOnlyServerRequest)
    }
}
