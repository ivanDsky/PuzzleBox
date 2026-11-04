package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

object P3ViewModelShape : Rule {
    override val id = "P3"

    private val IMPL_MODULE = Regex(""":feature:[^:]+:impl""")
    private val MUTABLE_FACTORY = Regex("""^[Mm]utable\w*\(""")

    override fun check(tree: SourceTree): RuleResult {
        var subjects = 0
        val violations = tree.scope(SourceSetKind.PRODUCTION).files.toList().mapNotNull { ko ->
            val file = tree.fileFor(ko)
            val problems = ko.classes(includeNested = false).filter(::isSubject).flatMap { cls ->
                subjects++
                problems(cls, file.module.path)
            }
            if (problems.isEmpty()) null else Violation(file.path, null, problems.joinToString("; "))
        }
        return RuleResult(subjects, violations)
    }

    private fun isSubject(cls: KoClassDeclaration) = extendsViewModel(cls) || cls.name.endsWith("ViewModel")

    private fun extendsViewModel(cls: KoClassDeclaration) = cls.parents().any { it.name.substringBefore('<') == "ViewModel" }

    private fun problems(cls: KoClassDeclaration, modulePath: String): List<String> = buildList {
        val extends = extendsViewModel(cls)
        val named = cls.name.endsWith("ViewModel")
        if (extends && !named) add("${cls.name} extends ViewModel but is not named *ViewModel")
        if (named && !extends) add("${cls.name} is named *ViewModel but does not extend ViewModel")
        if (!IMPL_MODULE.matches(modulePath)) add("${cls.name} lives in $modulePath, not :feature:<x>:impl")
        cls.properties(includeNested = false).filter(::exposesMutableState).forEach {
            add("${cls.name}.${it.name} is public mutable state: expose a read-only type, keep the Mutable* private")
        }
    }

    /** Lowercase factories (`mutableStateOf`) count as well as `MutableStateFlow(...)`: Compose state leaks the same way. */
    private fun exposesMutableState(property: KoPropertyDeclaration): Boolean {
        if (property.hasPrivateModifier) return false
        if (property.isVar) return true
        val head = property.text.substringBefore('=')
        val declared = if (':' in head) head.substringAfter(':').trim() else null
        if (declared != null) return declared.startsWith("Mutable")
        return MUTABLE_FACTORY.containsMatchIn(property.text.substringAfter('=', "").trim())
    }
}
