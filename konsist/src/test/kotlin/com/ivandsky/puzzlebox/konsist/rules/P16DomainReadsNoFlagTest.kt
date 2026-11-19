package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P16: no production file of `:core:domain`, its `di` package included, names the flags package
 * `com.ivandsky.puzzlebox.common.flags`: no import of it (plain, star or aliased), no inline qualified name in it, and
 * no file declared in it or below it. The subjects are the module's production files. Comments and string contents
 * never count; test sources and other modules are not scanned.
 *
 * Limits: the match is by name, with no type resolution. A flag reached through a typealias or a wrapper declared in
 * another module, or a flag's value passed in by its Koin definition, is not seen; the second is the intended shape.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md); flag ownership: ADR-0022.
 */
class P16DomainReadsNoFlagTest {
    private val domain = "core/domain/src/main/kotlin/com/ivandsky/puzzlebox/domain"

    @Test
    fun `P16 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P16DomainReadsNoFlag)
    }

    @Test
    fun `P16 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P16DomainReadsNoFlag,
            "P16",
            9,
            setOf(
                Violation("$domain/di/DomainModule.kt", null, ""),
                Violation("$domain/StarImport.kt", null, ""),
                Violation("$domain/AliasImport.kt", null, ""),
                Violation("$domain/InlineUse.kt", null, ""),
                Violation("core/domain/src/main/kotlin/com/ivandsky/puzzlebox/common/flags/DomainFlag.kt", null, ""),
                Violation("core/domain/src/debug/kotlin/com/ivandsky/puzzlebox/domain/DebugReader.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P16 hint names every flags reference in the file`() {
        val hint = P16DomainReadsNoFlag.check(SourceTree.fixture("P16")).violations
            .single { it.path == "$domain/di/DomainModule.kt" }.hint
        assertEquals(
            "remove com.ivandsky.puzzlebox.common.flags.ReleaseFlag, com.ivandsky.puzzlebox.common.flags.ReleaseFlags, " +
                "com.ivandsky.puzzlebox.common.flags.isEnabled: take the flag's value as a Boolean constructor " +
                "parameter and read the flag in the Koin module of the feature that owns it (ADR-0022 amendment 2026-11-18)",
            hint,
        )
    }

    @Test
    fun `P16 hint names a file declared in the flags package once`() {
        val hint = P16DomainReadsNoFlag.check(SourceTree.fixture("P16")).violations
            .single { it.path == "core/domain/src/main/kotlin/com/ivandsky/puzzlebox/common/flags/DomainFlag.kt" }.hint
        assertEquals(
            "remove com.ivandsky.puzzlebox.common.flags: take the flag's value as a Boolean constructor parameter and " +
                "read the flag in the Koin module of the feature that owns it (ADR-0022 amendment 2026-11-18)",
            hint,
        )
    }

    @Test
    fun `P16 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P16DomainReadsNoFlag)
    }
}
