package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P7: production files of Android-free modules (`puzzlebox.jvm.library` plus `:core:domain`) reference no
 * `android.`, `androidx.` or `org.koin.android`, by import or by inline qualified name; mentions in comments and
 * strings do not count. Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P7AndroidFreeTest {
    private val sample = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `P7 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P7AndroidFree)
    }

    @Test
    fun `P7 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P7AndroidFree,
            "P7",
            11,
            setOf(
                Violation("$sample/BadAndroid.kt", null, ""),
                Violation("$sample/BadAndroidx.kt", null, ""),
                Violation("$sample/BadKoinAndroid.kt", null, ""),
                Violation("$sample/MultiBad.kt", null, ""),
                Violation("$sample/InlineQualified.kt", null, ""),
                Violation("$sample/InlineAndroidx.kt", null, ""),
                Violation("core/domain/src/main/kotlin/com/ivandsky/puzzlebox/domain/UseCase.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P7 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P7AndroidFree)
    }
}
