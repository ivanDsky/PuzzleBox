package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

/** Data classes are the results use cases return, not use cases, so they are not judged. */
class P4UseCaseShape(val verbs: Set<String> = DEFAULT_VERBS) : Rule {
    override val id = "P4"

    override fun check(tree: SourceTree): RuleResult {
        val subjects = tree.scope(SourceSetKind.PRODUCTION).classes(includeNested = false)
            .filter { it.hasPublicOrDefaultModifier && !it.hasDataModifier }
            .map { it to tree.fileFor(it.containingFile) }
            .filter { (_, file) -> file.module.path == MODULE && file.packageName.substringAfterLast('.') != "di" }
        val violations = subjects.groupBy({ it.second.path }, { it.first }).mapNotNull { (path, classes) ->
            val problems = classes.mapNotNull(::problem)
            if (problems.isEmpty()) null else Violation(path, null, problems.joinToString("; "))
        }
        return RuleResult(subjects.size, violations)
    }

    private fun problem(declaration: KoClassDeclaration): String? {
        val name = declaration.name
        val verb = verbs.firstOrNull { name.startsWith(it) && name.getOrNull(it.length)?.isUpperCase() == true }
        if (verb == null) return "$name: start with a verb (${verbs.sorted().joinToString()}) followed by an uppercase letter"
        val invokes = declaration.functions(includeNested = false, includeLocal = false)
            .count { it.name == "invoke" && it.hasOperatorModifier && it.hasPublicOrDefaultModifier }
        return if (invokes == 1) null else "$name: needs exactly one public operator fun invoke, found $invokes"
    }

    companion object {
        private const val MODULE = ":core:domain"

        val DEFAULT_VERBS = setOf(
            "Get", "Observe", "Compute", "Submit", "Save", "Load", "Delete",
            "Update", "Create", "Sync", "Validate", "Record", "Start", "Finish",
        )
    }
}
