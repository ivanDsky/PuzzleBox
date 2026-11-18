package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S10: production code outside core:designsystem must not call Material's `ModalBottomSheet`; sheets go through
 * `AppModalBottomSheet`. Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S10AppModalBottomSheetTest {
    private val feature = "feature/sample/impl/src/main/kotlin/com/ivandsky/puzzlebox/sample"

    @Test
    fun `S10 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S10AppModalBottomSheet)
    }

    @Test
    fun `S10 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S10AppModalBottomSheet,
            "S10",
            3,
            setOf(
                Violation("$feature/RawSheet.kt", null, ""),
                Violation("$feature/QualifiedSheet.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S10 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S10AppModalBottomSheet)
    }
}
