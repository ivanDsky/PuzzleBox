package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S2: a production file over 300 physical lines must be split by responsibility; `*Previews.kt` is exempt.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S2FileLengthTest {
    private val dir = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `S2 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S2FileLength)
    }

    @Test
    fun `S2 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S2FileLength,
            "S2",
            3,
            setOf(Violation("$dir/TooLong.kt", 301, ""), Violation("$dir/Padded.kt", 301, "")),
        )
    }

    @Test
    fun `S2 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S2FileLength)
    }
}
