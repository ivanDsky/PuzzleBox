package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Previews
import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider

object S1OneTypePerFile : Rule {
    override val id = "S1"

    override fun check(tree: SourceTree): RuleResult {
        val scope = tree.scope(SourceSetKind.PRODUCTION)
        val previewAnnotations = Previews.annotationNames(scope)
        val files = scope.files.toList()
        val violations = files.mapNotNull { ko ->
            val names = publicTypeNames(ko, previewAnnotations)
            if (names.size > 1) {
                Violation(tree.fileFor(ko).path, names.size, "one public type per file: move ${names.drop(1).joinToString()} to their own files")
            } else {
                null
            }
        }
        return RuleResult(files.size, violations)
    }

    /** Classes, enums and annotations are KoClassDeclarations; plain functions and typealiases never count. */
    private fun publicTypeNames(file: KoFileDeclaration, previewAnnotations: Set<String>): List<String> {
        val types = file.classes(includeNested = false) + file.interfaces(includeNested = false) + file.objects(includeNested = false)
        val composables = file.functions(includeNested = false, includeLocal = false).filter { fn ->
            isPublic(fn) && fn.annotations.any { it.name == "Composable" } && !Previews.isPreview(fn, previewAnnotations)
        }
        return (types.filter(::isPublic).map { it.name } + composables.map { it.name })
    }

    private fun isPublic(declaration: KoVisibilityModifierProvider): Boolean = !declaration.hasPrivateModifier && !declaration.hasInternalModifier && !declaration.hasProtectedModifier
}
