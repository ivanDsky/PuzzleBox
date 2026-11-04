package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P3: a class extending `ViewModel` or named `*ViewModel` must do both, live in `:feature:<x>:impl`, and expose no
 * class-level non-private `var`, `Mutable*`-typed property or typeless `Mutable…(`/`mutable…(` initializer.
 * Decision: typeless lowercase factories such as `internal val y = mutableStateOf(0)` count (non-private Compose
 * state leaks like a MutableStateFlow); a declared type is judged by its `Mutable` prefix, case-sensitively.
 * A typeless property whose initializer or getter is a bare class-property name that is mutable by those rules
 * (`val state = _state`, `val state get() = _state`) counts too, private target or not; a declared read-only type passes.
 * Companion and nested-class properties and data classes in the same file are not judged.
 * Contract and allowlist rules: ADR-0009 (docs/adr/0009-in-module-structure-rules.md).
 */
class P3ViewModelShapeTest {
    private val impl = "feature/home/impl/src/main/kotlin/com/ivandsky/puzzlebox/home"
    private val api = "feature/home/api/src/main/kotlin/com/ivandsky/puzzlebox/home"

    @Test
    fun `P3 real tree is clean against allowlist`() {
        RuleCheck.assertClean(P3ViewModelShape)
    }

    @Test
    fun `P3 fixture yields exact violations`() {
        RuleCheck.assertFixture(
            P3ViewModelShape,
            "P3",
            setOf(
                Violation("$impl/HomeModel.kt", null, ""),
                Violation("$impl/FooViewModel.kt", null, ""),
                Violation("$api/ApiViewModel.kt", null, ""),
                Violation("$impl/PublicVarViewModel.kt", null, ""),
                Violation("$impl/MutableTypedViewModel.kt", null, ""),
                Violation("$impl/TypelessViewModel.kt", null, ""),
                Violation("$impl/InternalStateViewModel.kt", null, ""),
                Violation("$impl/MultiProblemViewModel.kt", null, ""),
                Violation("$impl/AliasLeakViewModel.kt", null, ""),
                Violation("$impl/GetterLeakViewModel.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P3 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P3ViewModelShape)
    }
}
