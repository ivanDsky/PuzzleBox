package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S12: production code outside core:designsystem must not call Material's `Popup`, `DropdownMenu` or
 * `ExposedDropdownMenu`; they go through `AppPopup` / `AppDropdownMenu`, which re-provide the Vibration gate inside the
 * popup window. Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S12AppWindowsTest {
    private val feature = "feature/sample/impl/src/main/kotlin/com/ivandsky/puzzlebox/sample"

    @Test
    fun `S12 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S12AppWindows)
    }

    @Test
    fun `S12 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S12AppWindows,
            "S12",
            4,
            setOf(
                Violation("$feature/RawPopup.kt", null, ""),
                Violation("$feature/RawMenu.kt", null, ""),
                Violation("$feature/QualifiedPopup.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S12 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S12AppWindows)
    }
}
