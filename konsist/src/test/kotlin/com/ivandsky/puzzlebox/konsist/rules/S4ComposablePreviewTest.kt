package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S4: every public top-level composable is called by a preview in its own module.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S4ComposablePreviewTest {
    private val alpha = "core/alpha/src/main/kotlin/com/ivandsky/puzzlebox/alpha"
    private val beta = "games/beta/src/main/kotlin/com/ivandsky/puzzlebox/games/beta"

    @Test
    fun `S4 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S4ComposablePreview)
    }

    @Test
    fun `S4 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S4ComposablePreview,
            "S4",
            11,
            setOf(
                Violation("$alpha/Uncovered.kt", null, ""),
                Violation("$beta/OtherModule.kt", null, ""),
                Violation("$beta/InComment.kt", null, ""),
                Violation("$beta/WordBoundary.kt", null, ""),
                Violation("$beta/ParentOnly.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S4 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S4ComposablePreview)
    }
}
