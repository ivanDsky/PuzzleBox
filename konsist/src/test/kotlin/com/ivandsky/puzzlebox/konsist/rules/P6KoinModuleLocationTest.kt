package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P6: a Koin `module {}` / `lazyModule {}` lives in a `di` package, or in a game module's root package.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P6KoinModuleLocationTest {
    private val feature = "feature/x/src/main/kotlin"

    @Test
    fun `P6 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P6KoinModuleLocation)
    }

    @Test
    fun `P6 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P6KoinModuleLocation,
            "P6",
            12,
            setOf(
                Violation("$feature/BadUi.kt", null, ""),
                Violation("$feature/BadArgs.kt", null, ""),
                Violation("$feature/BadLazy.kt", null, ""),
                Violation("$feature/BadAlias.kt", null, ""),
                Violation("$feature/BadLazyAlias.kt", null, ""),
                Violation("games/x/src/main/kotlin/BadFeed.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P6 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P6KoinModuleLocation)
    }
}
