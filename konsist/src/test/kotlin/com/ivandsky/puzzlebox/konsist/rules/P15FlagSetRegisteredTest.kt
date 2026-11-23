package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P15: every flag enum ([FlagEnums]) in production sources is registered by a `flagSet("<owner>", <Enum>.entries)`
 * call in `main` sources, in a package whose last segment is `di`, of the enum's own module or `:app`; the enum counts
 * as registered when the call's reference is its simple name, its qualified name or a dotted tail of it. Every
 * production `flagSet` call has that shape, with a plain string literal owner (an owner held in a variable or a
 * template cannot be checked); no owner is registered twice, in any production source set. The central `ReleaseFlag`
 * holds no entry: it is gone, and a new one is told to go to its owner's enum. The subjects are the flag enums and the production
 * `flagSet` calls; a `fun … flagSet` declaration, a comment, a string and test sources are not calls.
 *
 * Limits: the match is by name, with no type resolution, so two flag enums with one simple name share a bare
 * registration. A call through an import alias or a function reference is not seen. Whether the registering Koin
 * module is loaded by the app is not checked. One owner registered once in src/debug and once in src/release is
 * reported, though only one compiles; register it once in main.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md); flag ownership: ADR-0022.
 */
class P15FlagSetRegisteredTest {
    private val sample = "feature/sample/impl/src/main/kotlin/com/ivandsky/puzzlebox/sample"
    private val central = "core/common/src/main/kotlin/com/ivandsky/puzzlebox/common/flags/ReleaseFlag.kt"

    @Test
    fun `P15 real tree is clean against allowlist`() {
        RuleCheck.assertCleanWhenScopeMayBeEmpty(P15FlagSetRegistered)
    }

    @Test
    fun `P15 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P15FlagSetRegistered,
            "P15",
            19,
            setOf(
                Violation(central, null, ""),
                Violation("$sample/flags/UnregisteredFlag.kt", null, ""),
                Violation("$sample/flags/DebugOnlyFlag.kt", null, ""),
                Violation("$sample/flags/NotDiFlag.kt", null, ""),
                Violation("$sample/flags/CommentFlag.kt", null, ""),
                Violation("$sample/di/MalformedModule.kt", null, ""),
                Violation("feature/other/impl/src/main/kotlin/com/ivandsky/puzzlebox/other/flags/ForeignFlag.kt", null, ""),
                Violation("feature/other/impl/src/main/kotlin/com/ivandsky/puzzlebox/other/di/OtherModule.kt", null, ""),
                Violation("feature/twin/impl/src/main/kotlin/com/ivandsky/puzzlebox/twin/di/TwinModule.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P15 freeze names the added central entry`() {
        val hint = P15FlagSetRegistered.check(SourceTree.fixture("P15")).violations.single { it.path == central }.hint
        assertEquals(
            "ReleaseFlag is frozen: move AddedLater to the owner module's flag enum (ADR-0022 amendment 2026-11-18)",
            hint,
        )
    }

    @Test
    fun `P15 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P15FlagSetRegistered)
    }
}
