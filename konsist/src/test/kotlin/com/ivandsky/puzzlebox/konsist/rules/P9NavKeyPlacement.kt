package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import com.lemonappdev.konsist.api.declaration.combined.KoClassAndObjectDeclaration

/** ADR-0027: every NavKey has a row in the placement table, so no screen ships without a home and a chrome. */
object P9NavKeyPlacement : Rule {
    override val id = "P9"

    private const val NAV_KEY = "NavKey"
    private const val PLACEMENT_FILE = "app/src/main/kotlin/com/ivandsky/puzzlebox/app/nav/Placement.kt"
    private val CLASS_TOKEN = Regex("""\b([A-Za-z_]\w*)\s*::\s*class\b""")

    override fun check(tree: SourceTree): RuleResult {
        val scope = tree.scope(SourceSetKind.PRODUCTION)
        val declarations: List<KoClassAndObjectDeclaration> = scope.classes() + scope.objects()
        val keys = declarations.filter(::isNavKey)
        val rowed = tree.files(SourceSetKind.PRODUCTION)
            .firstOrNull { it.path == PLACEMENT_FILE }
            ?.let { file -> CLASS_TOKEN.findAll(file.codeText).map { it.groupValues[1] }.toSet() }
            .orEmpty()
        val violations = keys.groupBy { tree.fileFor(it.containingFile).path }.mapNotNull { (path, inFile) ->
            val missing = inFile.map { it.name }.filter { it !in rowed }
            if (missing.isEmpty()) null else Violation(path, null, hint(missing))
        }
        return RuleResult(keys.size, violations)
    }

    /** A direct `NavKey` supertype, plain or qualified; a class holding NavKeys is not one. */
    private fun isNavKey(declaration: KoClassAndObjectDeclaration): Boolean =
        declaration.parents().any { it.name.substringBefore('<').trim().substringAfterLast('.') == NAV_KEY }

    private fun hint(missing: List<String>): String =
        "${missing.joinToString()} has no row: add a PlacementRow(<Name>::class, Placement(…)) entry to " +
            "placementRows in $PLACEMENT_FILE; rules in docs/adr/0027-navigation-placement.md"
}
