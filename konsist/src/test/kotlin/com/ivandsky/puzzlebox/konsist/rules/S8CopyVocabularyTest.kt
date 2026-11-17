package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S8: production string literals use one word per concept and US spelling (spec S1).
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S8CopyVocabularyTest {
    private val dir = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `S8 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S8CopyVocabulary)
    }

    @Test
    fun `S8 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S8CopyVocabulary,
            "S8",
            11,
            setOf(
                Violation("$dir/Colour.kt", 2, ""),
                Violation("$dir/Favourite.kt", 1, ""),
                Violation("$dir/Leaderboard.kt", 2, ""),
                Violation("$dir/Crown.kt", 1, ""),
                Violation("$dir/Phone.kt", 1, ""),
                Violation("$dir/Rounds.kt", 1, ""),
                Violation("$dir/Neighbour.kt", 1, ""),
                Violation("$dir/Cancelled.kt", 1, ""),
                Violation("$dir/Nested.kt", 3, ""),
            ),
        )
    }

    @Test
    fun `S8 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S8CopyVocabulary)
    }
}
