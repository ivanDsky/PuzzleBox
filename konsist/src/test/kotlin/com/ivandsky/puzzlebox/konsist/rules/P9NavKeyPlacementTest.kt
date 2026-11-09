package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P9: every production class or object with a direct `NavKey` supertype has a `<Name>::class` token in the
 * placement file `app/.../nav/Placement.kt`. A mention in a comment or a string is not a row. Contract and
 * allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md); placement rules: ADR-0027.
 */
class P9NavKeyPlacementTest {
    private val api = "feature/x/api/src/main/kotlin/com/ivandsky/puzzlebox/x/api"

    @Test
    fun `P9 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P9NavKeyPlacement)
    }

    @Test
    fun `P9 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P9NavKeyPlacement,
            "P9",
            4,
            setOf(
                Violation("$api/MissingRowKey.kt", null, ""),
                Violation("$api/CommentedOnlyKey.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P9 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P9NavKeyPlacement)
    }
}
