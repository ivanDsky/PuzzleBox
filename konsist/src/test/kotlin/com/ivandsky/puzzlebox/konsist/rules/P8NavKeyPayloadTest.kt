package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P8: every production class or object with a direct `NavKey` supertype declares only `String` and primitive
 * properties (nullable allowed): ids, ISO day strings, times, counts. A `ByteArray`, a collection or a model type
 * fails. Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P8NavKeyPayloadTest {
    private val api = "feature/x/api/src/main/kotlin/com/ivandsky/puzzlebox/x/api"

    @Test
    fun `P8 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P8NavKeyPayload)
    }

    @Test
    fun `P8 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P8NavKeyPayload,
            "P8",
            5,
            setOf(
                Violation("$api/BadByteArrayKey.kt", null, ""),
                Violation("$api/BadListKey.kt", null, ""),
                Violation("$api/BadModelKey.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P8 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P8NavKeyPayload)
    }
}
