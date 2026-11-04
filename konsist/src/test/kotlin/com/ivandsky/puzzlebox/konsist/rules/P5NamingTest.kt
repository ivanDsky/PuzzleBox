package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P5: no type named `*Manager`, `*Helper`, `*Util(s)` or `*Impl`; a `Fake<X>` must have a supertype ending in `<X>`;
 * a class implementing a project Repository/Source/Gateway/Store/Service/Provider interface is named `<Qualifier><Interface>`.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P5NamingTest {
    private val main = "games/sample/src/main/kotlin/com/ivandsky/puzzlebox/games/sample"
    private val test = "games/sample/src/test/kotlin/com/ivandsky/puzzlebox/games/sample"

    @Test
    fun `P5 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P5Naming)
    }

    @Test
    fun `P5 fixture yields exact violations`() {
        val bad = listOf(
            "$main/GameManager.kt",
            "$main/StringHelper.kt",
            "$main/DateUtil.kt",
            "$main/DateUtils.kt",
            "$main/GameRepositoryImpl.kt",
            "$main/FakeThing.kt",
            "$main/FakeClock.kt",
            "$main/SeedSource.kt",
            "$main/NestedManager.kt",
            "$main/PuzzleHelper.kt",
            "$main/Several.kt",
            "$main/Pages.kt",
            "$main/CacheStore.kt",
            "$test/FooImpl.kt",
            "$test/Dummy.kt",
            "$test/Stub.kt",
        )
        RuleCheck.assertFixture(P5Naming, "P5", 33, bad.map { Violation(it, null, "") }.toSet())
    }

    @Test
    fun `P5 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P5Naming)
    }
}
