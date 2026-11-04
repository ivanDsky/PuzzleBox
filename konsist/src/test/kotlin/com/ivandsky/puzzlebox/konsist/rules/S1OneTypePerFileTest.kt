package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S1: a production file holds at most one public top-level type (class, interface, object, enum, annotation
 * or public non-preview composable). Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S1OneTypePerFileTest {
    private val dir = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `S1 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S1OneTypePerFile)
    }

    @Test
    fun `S1 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S1OneTypePerFile,
            "S1",
            16,
            setOf(
                Violation("$dir/TwoClasses.kt", 2, ""),
                Violation("$dir/ClassAndEnum.kt", 2, ""),
                Violation("$dir/ClassAndComposable.kt", 2, ""),
                Violation("$dir/ObjectAndInterface.kt", 2, ""),
                Violation("$dir/DataAndSealed.kt", 2, ""),
                Violation("$dir/ClassAndNotPreview.kt", 2, ""),
            ),
        )
    }

    @Test
    fun `S1 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S1OneTypePerFile)
    }
}
