package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndObjectDeclaration
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withOperatorModifier
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withPublicOrDefaultModifier
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withoutDataModifier
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withoutEnumModifier
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withoutSealedModifier
import com.lemonappdev.konsist.api.ext.list.withName

/** Data classes and objects, enums and sealed classes are the results use cases return, not use cases. */
class P4UseCaseShape(val verbs: Set<String> = DEFAULT_VERBS) : Rule {
    override val id = "P4"

    override fun check(tree: SourceTree): RuleResult {
        val scope = tree.scope(SourceSetKind.PRODUCTION)
        val classes = scope.classes(includeNested = false).withoutEnumModifier().withoutSealedModifier()
        val objects = scope.objects(includeNested = false)
        val subjects = (classes + objects)
            .withPublicOrDefaultModifier()
            .withoutDataModifier()
            .map { it to tree.fileFor(it.containingFile) }
            .filter { (_, file) -> inScope(file) && file.packageName.substringAfterLast('.') != "di" }
        val violations = subjects.groupBy({ it.second.path }, { it.first }).mapNotNull { (path, declarations) ->
            val problems = declarations.mapNotNull(::problem)
            if (problems.isEmpty()) null else Violation(path, null, problems.joinToString("; "))
        }
        return RuleResult(subjects.size, violations)
    }

    /** `:core:domain`, plus the `domain` package and its sub-packages of every `:feature:<x>:impl`. */
    private fun inScope(file: SourceFile): Boolean {
        val module = file.module
        if (module.path == MODULE) return true
        if (!FEATURE_IMPL.matches(module.path)) return false
        val domain = "${module.packageRoot}.domain"
        return file.packageName == domain || file.packageName.startsWith("$domain.")
    }

    private fun problem(declaration: KoClassAndObjectDeclaration): String? {
        val name = declaration.name
        val verb = verbs.firstOrNull { name.startsWith(it) && name.getOrNull(it.length)?.isUpperCase() == true }
        if (verb == null) return "$name: start with a verb (${verbs.sorted().joinToString()}) followed by an uppercase letter"
        // Every public `invoke` is an entry point; exactly one, and it must be the operator.
        val invokes = declaration.functions(includeNested = false, includeLocal = false)
            .withName("invoke")
            .withPublicOrDefaultModifier()
        return if (invokes.size == 1 && invokes.single().hasOperatorModifier) {
            null
        } else {
            "$name: needs exactly one public invoke, an operator fun; found ${invokes.size} " +
                "(${invokes.withOperatorModifier().size} operator)"
        }
    }

    companion object {
        private const val MODULE = ":core:domain"
        private val FEATURE_IMPL = Regex(""":feature:[^:]+:impl""")

        val DEFAULT_VERBS = setOf(
            "Get", "Observe", "Compute", "Submit", "Save", "Load", "Delete",
            "Update", "Create", "Sync", "Validate", "Record", "Start", "Finish", "Import", "List", "Report",
        )
    }
}
