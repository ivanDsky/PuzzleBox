package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.Scan
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S0NoSuppress : Rule {
    override val id = "S0"
    override val kinds = setOf(SourceSetKind.PRODUCTION, SourceSetKind.TEST)

    private const val HINT = "remove the suppression; fix the code or ask for a ledgered Ruling"

    /**
     * Any `Suppress(` in code: `@Suppress`, `@kotlin.Suppress`, every use-site target (`@file:`, `@get:`, …) and the
     * bracket form `@file:[A Suppress(…)]`. Matched in [SourceFile.codeText], so a mention inside a string is not one.
     * Text, not Konsist annotations: those miss `@Suppress` on a getter, a function parameter and an expression.
     */
    private val suppress = Regex("""(?<![\w.])(?:kotlin\s*\.\s*)?Suppress\s*\(""")
    private val literal = Regex(""""((?:\\.|[^"\\])*)"""")
    private val ruleTarget = Regex("""^(konsist\.|[SP]\d+)""")

    override fun check(tree: SourceTree): RuleResult {
        val files = kinds.flatMap { tree.files(it) }
        val violations = files.filter { file ->
            suppress.findAll(file.codeText).any { match ->
                val open = match.range.last
                // Balanced on codeText, where string contents are blank; the arguments are read from strippedText.
                val end = Scan.closing(file.codeText, open) ?: file.codeText.length
                literal.findAll(file.strippedText.substring(open, end)).any { ruleTarget.containsMatchIn(it.groupValues[1]) }
            }
        }.map { Violation(it.path, null, HINT) }
        return RuleResult(files.size, violations)
    }
}
