package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * S13: production code outside `VibratorHapticFeedback` must not play the Vibrator (`vibrate(`, `VibratorManager`,
 * `getSystemService` for the Vibrator); a buzz that skips it skips the Vibration switch. Contract and allowlist rules:
 * ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class S13VibratorBehindGateTest {
    private val app = "app/src/main/kotlin/com/ivandsky/puzzlebox/app"
    private val feature = "feature/sample/impl/src/main/kotlin/com/ivandsky/puzzlebox/sample"

    @Test
    fun `S13 real tree is clean against allowlist`() {
        RuleCheck.assertClean(S13VibratorBehindGate)
    }

    @Test
    fun `S13 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            S13VibratorBehindGate,
            "S13",
            6,
            setOf(
                Violation("$app/SecondGate.kt", null, ""),
                Violation("$feature/RawVibrate.kt", null, ""),
                Violation("$feature/ManagerLookup.kt", null, ""),
                Violation("$feature/ServiceLookup.kt", null, ""),
                Violation("$feature/LegacyService.kt", null, ""),
            ),
        )
    }

    @Test
    fun `S13 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(S13VibratorBehindGate)
    }
}
