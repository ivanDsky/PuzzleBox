package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object P6KoinModuleLocation : Rule {
    override val id = "P6"

    private const val HINT = "move the Koin module to a `di` package"
    private val KOIN_BUILDERS = setOf("module", "lazyModule")
    private val builderAlias = Regex("""(?m)^\s*import\s+org\.koin\.dsl\.(\w+)\s+as\s+(\w+)""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION)
        val violations = files.mapNotNull { file ->
            val pkg = file.packageName
            val inDi = pkg.substringAfterLast('.') == "di"
            // ADR-0008 "Adding a game": game modules keep their Koin module in the root package.
            val isGameRoot = file.module.path.startsWith(":games:") && pkg == file.module.packageRoot
            if (declaration(file.codeText).containsMatchIn(file.codeText) && !inDi && !isGameRoot) {
                Violation(file.path, null, HINT)
            } else {
                null
            }
        }
        return RuleResult(files.size, violations)
    }

    /** `module {`, `module(…) {` and the same for `lazyModule`, plus any name the file imports either one as. */
    private fun declaration(code: String): Regex {
        val aliases = builderAlias.findAll(code).filter { it.groupValues[1] in KOIN_BUILDERS }.map { it.groupValues[2] }
        val names = (KOIN_BUILDERS + aliases).joinToString("|") { Regex.escape(it) }
        return Regex("""\b(?:$names)\s*(?:\([^)]*\)\s*)?\{""")
    }
}
