package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S6: production code in `:games:*` must not use an infinite animation API.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S6NoInfiniteAnimationTest {
    private val dir = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `S6 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S6NoInfiniteAnimation)
    }

    @Test
    fun `S6 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S6NoInfiniteAnimation,
            "S6",
            5,
            setOf(
                Violation("$dir/Transition.kt", null, ""),
                Violation("$dir/Repeatable.kt", null, ""),
                Violation("$dir/FrameLoop.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S6 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S6NoInfiniteAnimation)
    }
}
