package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S7: production code must not read the system night flag outside the places that resolve the app theme.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S7NoSystemDarkThemeTest {
    private val sample = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"
    private val designsystem = "core/designsystem/src/main/kotlin/com/ivandsky/puzzlebox/designsystem"
    private val app = "app/src/main/kotlin/com/example/fixture/app"

    @Test
    fun `S7 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S7NoSystemDarkTheme)
    }

    @Test
    fun `S7 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S7NoSystemDarkTheme,
            "S7",
            5,
            setOf(
                Violation("$sample/Bad.kt", null, ""),
                Violation("$sample/UiMode.kt", null, ""),
                Violation("$designsystem/OtherTheme.kt", null, ""),
                Violation("$app/Screen.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S7 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S7NoSystemDarkTheme)
    }
}
