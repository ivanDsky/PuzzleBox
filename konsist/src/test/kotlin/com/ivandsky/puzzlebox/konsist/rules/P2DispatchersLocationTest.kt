package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P2: `Dispatchers` may be referenced only in the `.common.dispatcher` package of `:core:common` or in module `:core:testing`.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P2DispatchersLocationTest {
    private val dir = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `P2 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P2DispatchersLocation)
    }

    @Test
    fun `P2 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P2DispatchersLocation,
            "P2",
            setOf(
                Violation("$dir/ImportBad.kt", null, ""),
                Violation("$dir/MemberImportBad.kt", null, ""),
                Violation("$dir/InlineBad.kt", null, ""),
                Violation("$dir/TemplateBad.kt", null, ""),
                Violation("$dir/common/dispatcher/ElsewhereBad.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P2 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P2DispatchersLocation)
    }
}
