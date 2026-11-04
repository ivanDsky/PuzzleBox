package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object P6KoinModuleLocation : Rule {
    override val id = "P6"

    private const val HINT = "move the Koin module to a `di` package"
    private val declaration = Regex("""\bmodule\s*\{|\bmodule\s*\([^)]*\)\s*\{|\blazyModule\s*\{""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION)
        val violations = files.mapNotNull { file ->
            val pkg = file.packageName
            val inDi = pkg.substringAfterLast('.') == "di"
            // ADR-0008 "Adding a game": game modules keep their Koin module in the root package.
            val isGameRoot = file.module.path.startsWith(":games:") && pkg == file.module.packageRoot
            if (declaration.containsMatchIn(file.strippedText) && !inDi && !isGameRoot) {
                Violation(file.path, null, HINT)
            } else {
                null
            }
        }
        return RuleResult(files.size, violations)
    }
}
