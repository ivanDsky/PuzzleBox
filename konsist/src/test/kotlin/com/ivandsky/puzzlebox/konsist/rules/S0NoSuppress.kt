package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S0NoSuppress : Rule {
    override val id = "S0"
    override val kinds = setOf(SourceSetKind.PRODUCTION, SourceSetKind.TEST)

    private const val HINT = "remove the suppression; fix the code or ask for a ledgered Ruling"

    // Arguments end at the first ')': rule IDs never contain one, and a ')' inside an earlier string only truncates the scan.
    private val annotation = Regex("""@(?:file:)?(?:kotlin\.)?Suppress\s*\(([^)]*)\)""")
    private val literal = Regex(""""([^"]*)"""")
    private val ruleTarget = Regex("""^(konsist\.|[SP]\d+)""")

    override fun check(tree: SourceTree): RuleResult {
        val files = kinds.flatMap { tree.files(it) }
        val violations = files.filter { file ->
            annotation.findAll(file.strippedText).any { match ->
                literal.findAll(match.groupValues[1]).any { ruleTarget.containsMatchIn(it.groupValues[1]) }
            }
        }.map { Violation(it.path, null, HINT) }
        return RuleResult(files.size, violations)
    }
}
