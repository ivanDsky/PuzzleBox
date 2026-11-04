package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.CommentStripper
import com.ivandsky.puzzlebox.konsist.core.Previews
import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withPublicOrDefaultModifier
import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.ext.list.withTopLevel

object S4ComposablePreview : Rule {
    override val id = "S4"

    private const val COMPOSABLE = "Composable"

    override fun check(tree: SourceTree): RuleResult {
        val scope = tree.scope(SourceSetKind.PRODUCTION)
        val previewAnnotations = Previews.annotationNames(scope)
        val functions = scope.functions(includeNested = true)
        val previews = functions.filter { Previews.isPreview(it, previewAnnotations) }
        val calledByModule = previews
            .groupBy { tree.fileFor(it.containingFile).module.path }
            .mapValues { (_, fns) -> fns.joinToString("\n") { CommentStripper.strip(it.text) } }

        val judged = functions.withTopLevel().withPublicOrDefaultModifier().withAnnotationNamed(COMPOSABLE) - previews.toSet()
        val uncovered = judged.mapNotNull { fn ->
            val file = tree.fileFor(fn.containingFile)
            val called = calledByModule[file.module.path].orEmpty()
            if (callPattern(fn.name).containsMatchIn(called)) null else file.path to fn.name
        }
        val violations = uncovered.groupBy({ it.first }, { it.second }).map { (path, names) ->
            Violation(path, null, "add a preview calling ${names.joinToString(", ")} (see preview-compose)")
        }
        return RuleResult(judged.size, violations)
    }

    /** `Name(` per A7, plus the trailing-lambda call `Name {`: wrappers such as PuzzleBoxTheme are only ever called that way. */
    private fun callPattern(name: String) = Regex("""\b${Regex.escape(name)}\s*[({]""")
}
