package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndObjectDeclaration

/** A5: a NavKey carries only id strings, ISO day strings and primitives — never a blob, a collection or a model. */
object P8NavKeyPayload : Rule {
    override val id = "P8"

    private const val NAV_KEY = "NavKey"
    private val ALLOWED = setOf("String", "Int", "Long", "Boolean", "Short", "Byte", "Char", "Float", "Double")

    override fun check(tree: SourceTree): RuleResult {
        val scope = tree.scope(SourceSetKind.PRODUCTION)
        val declarations: List<KoClassAndObjectDeclaration> = scope.classes() + scope.objects()
        val keys = declarations.filter(::isNavKey)
        val violations = keys.groupBy { tree.fileFor(it.containingFile).path }.mapNotNull { (path, inFile) ->
            val problems = inFile.flatMap(::problems)
            if (problems.isEmpty()) null else Violation(path, null, problems.joinToString("; "))
        }
        return RuleResult(keys.size, violations)
    }

    /** A direct `NavKey` supertype, plain or qualified; a class holding NavKeys is not one. */
    private fun isNavKey(declaration: KoClassAndObjectDeclaration): Boolean =
        declaration.parents().any { it.name.substringBefore('<').trim().substringAfterLast('.') == NAV_KEY }

    private fun problems(key: KoClassAndObjectDeclaration): List<String> =
        key.properties(includeNested = false).mapNotNull { property ->
            when (val type = property.type?.bareSourceType?.substringAfterLast('.')) {
                null -> "${key.name}.${property.name} has no declared type: declare a String or primitive"
                in ALLOWED -> null
                else -> "${key.name}.${property.name} is $type: carry an id string, an ISO day string or a primitive"
            }
        }
}
