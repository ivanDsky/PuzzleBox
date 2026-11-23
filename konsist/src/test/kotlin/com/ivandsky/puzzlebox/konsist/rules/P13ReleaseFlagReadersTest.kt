package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P13: every entry of a flag enum that is not explicitly dark (a named `releaseDefault = false`) is named in at least
 * one production file outside the enum's own file, in a source set that is not `debug*` and outside `:core:testing`.
 * Flag enums are the central `ReleaseFlag` in `com.ivandsky.puzzlebox.common.flags` and every enum, top-level or nested,
 * whose supertypes include `ReleaseFlagKey` or `ReleaseFlag` (plain or qualified), so an entry that moves to an owner
 * enum (`PlayFlag`) stays judged; the subjects are the flag enums. An enum named `ReleaseFlag` in another package is
 * not one. Dark entries may wait for their first gate, as a trunk feature's ticket 01 adds its flag alone; a deleted
 * entry that a rebase brings back is shipped, so it is judged. A name counts as `<Enum>.<Entry>` (plain, fully
 * qualified, or through an alias of the enum), or as a bare `<Entry>` or alias in a file that imports the entry or
 * `<Enum>.*`. Comments and string contents never count, so a KDoc link or a label is not a reader. Code that walks
 * `entries`, like the debug Release flags panel and `ShippedReleaseFlags`, names no entry, and test sources are not
 * scanned.
 *
 * Limits: the match is by name, with no type resolution. A bare name in a file that imports the entry counts even when
 * it resolves to another symbol, and two flag enums with one simple name share their qualified reads.
 * `valueOf("<Entry>")` and a read through a variable that holds an entry are not seen. Any reference counts as a
 * reader, whether it gates a branch or only lists the entry. A dark entry with no reader is never flagged, however
 * long it waits. A tree with no flag enum at all fails as an empty scope.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md); flag lifecycle: ADR-0022.
 */
class P13ReleaseFlagReadersTest {
    @Test
    fun `P13 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P13ReleaseFlagReaders)
    }

    @Test
    fun `P13 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P13ReleaseFlagReaders,
            "P13",
            5,
            setOf(
                Violation("core/common/src/main/kotlin/com/ivandsky/puzzlebox/common/flags/ReleaseFlag.kt", null, ""),
                Violation("feature/sample/impl/src/main/kotlin/com/ivandsky/puzzlebox/sample/flags/SampleFlag.kt", null, ""),
                Violation("feature/sample/impl/src/main/kotlin/com/ivandsky/puzzlebox/sample/flags/SampleFlags.kt", null, ""),
                Violation("feature/sample/impl/src/main/kotlin/com/ivandsky/puzzlebox/sample/flags/SupertypeFlags.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P13 fixture names exactly the shipped entries without a reader`() {
        assertEquals(
            setOf(
                "ReleaseFlag.CommentOnly",
                "ReleaseFlag.DebugOnly",
                "ReleaseFlag.TestOnly",
                "ReleaseFlag.TestingOnly",
                "ReleaseFlag.SelfOnly",
                "ReleaseFlag.PositionalDark",
                "SampleFlag.OwnerUnread",
                "Flag.NestedUnread",
                "QualifiedKeyFlag.QualifiedKeyUnread",
                "RenamedFlag.RenamedUnread",
            ),
            P13ReleaseFlagReaders.unread(SourceTree.fixture("P13")).toSet(),
        )
    }

    @Test
    fun `P13 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P13ReleaseFlagReaders)
    }
}
