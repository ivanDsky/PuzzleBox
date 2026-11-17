package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S9: production code must not call `View.performHapticFeedback` or use `HapticFeedbackConstants`; every haptic goes
 * through `LocalHapticFeedback`. Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S9HapticsThroughComposeTest {
    private val dir = "core/ui/src/main/kotlin/com/ivandsky/puzzlebox/core/ui"

    @Test
    fun `S9 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S9HapticsThroughCompose)
    }

    @Test
    fun `S9 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S9HapticsThroughCompose,
            "S9",
            6,
            setOf(
                Violation("$dir/Constants.kt", null, ""),
                Violation("$dir/ViewReceiverCall.kt", null, ""),
                Violation("$dir/LocalViewCall.kt", null, ""),
                Violation("$dir/ViewCompatCall.kt", null, ""),
                Violation("$dir/LiteralCall.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S9 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S9HapticsThroughCompose)
    }
}
