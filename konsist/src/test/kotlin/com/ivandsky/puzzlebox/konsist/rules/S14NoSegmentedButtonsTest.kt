package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S14: production code must not use Material's segmented button (`SegmentedButton`, `SingleChoiceSegmentedButtonRow`,
 * `MultiChoiceSegmentedButtonRow`, `SegmentedButtonDefaults`); the mode switches are `ChoiceTiles` / `ChoiceRows`. Contract
 * and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S14NoSegmentedButtonsTest {
    private val feature = "feature/sample/impl/src/main/kotlin/com/ivandsky/puzzlebox/sample"

    @Test
    fun `S14 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S14NoSegmentedButtons)
    }

    @Test
    fun `S14 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S14NoSegmentedButtons,
            "S14",
            4,
            setOf(
                Violation("$feature/RawRow.kt", null, ""),
                Violation("$feature/MultiRow.kt", null, ""),
                Violation("$feature/ImportedDefaults.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S14 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S14NoSegmentedButtons)
    }
}
