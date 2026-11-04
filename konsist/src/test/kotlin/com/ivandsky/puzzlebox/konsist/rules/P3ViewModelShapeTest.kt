package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.RuleCheck
import com.ivandsky.puzzlebox.konsist.core.Violation
import org.junit.Test

/**
 * P3: a class extending `ViewModel` (directly or through project base classes, by simple name) or named `*ViewModel`
 * must do both, live in `:feature:<x>:impl`, and expose no class-level non-private `var`, `Mutable*`-typed property
 * or typeless `Mutable…(`/`mutable…(` initializer. Primary-constructor `val`/`var` parameters are properties too.
 * Decision (A11): typeless lowercase factories such as `internal val y = mutableStateOf(0)` count (non-private
 * Compose state leaks like a MutableStateFlow); a declared type is judged by its `Mutable` prefix, case-sensitively.
 * A typeless property whose initializer or getter is a class-property name that is a `var` or mutable by those rules
 * counts too, private target or not, also through `this.` and receiver-returning `.also`/`.apply` calls
 * (`val state = _state`, `val state get() = _state`, `val state = _state.also { }`); a declared read-only type passes.
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
            25,
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
                Violation("$impl/AlsoChainViewModel.kt", null, ""),
                Violation("$impl/ConstructorStateViewModel.kt", null, ""),
                Violation("$impl/ConstructorVarViewModel.kt", null, ""),
                Violation("$impl/CtorAliasViewModel.kt", null, ""),
                Violation("$impl/VarAliasViewModel.kt", null, ""),
                Violation("$impl/AnnotatedAliasViewModel.kt", null, ""),
                Violation("$impl/ChildScreenModel.kt", null, ""),
                Violation("$impl/LeakyChildViewModel.kt", null, ""),
                Violation("$impl/GrandChildScreen.kt", null, ""),
                Violation("$impl/QualifiedTypeViewModel.kt", null, ""),
                Violation("$impl/AnnotatedGetterViewModel.kt", null, ""),
            ),
        )
    }

    @Test
    fun `P3 add-a-game is clean`() {
        RuleCheck.assertAddAGameClean(P3ViewModelShape)
    }
}
