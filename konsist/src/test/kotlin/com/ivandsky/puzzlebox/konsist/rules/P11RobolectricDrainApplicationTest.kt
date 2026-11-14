package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P11: an app Robolectric test class (`@RunWith(RobolectricTestRunner::class)` or `AndroidJUnit4`) must have a class-level
 * `@Config(application = DrainTestApplication::class)`. Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P11RobolectricDrainApplicationTest {
    private val test = "app/src/test/kotlin/com/example/fixture/app"
    private val debug = "app/src/testDebug/kotlin/com/example/fixture/app"
    private val release = "app/src/testRelease/kotlin/com/example/fixture/app"

    @Test
    fun `P11 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P11RobolectricDrainApplication)
    }

    @Test
    fun `P11 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P11RobolectricDrainApplication,
            "P11",
            18,
            setOf(
                Violation("$test/BadNoConfig.kt", null, ""),
                Violation("$test/BadConfigWithoutApplication.kt", null, ""),
                Violation("$test/BadPlainApplication.kt", null, ""),
                Violation("$test/BadQualifiedPlainApplication.kt", null, ""),
                Violation("$test/BadMethodLevelOnly.kt", null, ""),
                Violation("$test/BadLookalikeApplication.kt", null, ""),
                Violation("$test/BadCommentedConfig.kt", null, ""),
                Violation("$test/BadSecondClass.kt", null, ""),
                Violation("$test/BadAndroidJUnit4.kt", null, ""),
                Violation("$debug/BadDebug.kt", null, ""),
                Violation("$release/BadRelease.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P11 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P11RobolectricDrainApplication)
    }
}
