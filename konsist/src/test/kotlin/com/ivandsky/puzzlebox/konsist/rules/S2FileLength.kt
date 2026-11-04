package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S2FileLength : Rule {
    override val id = "S2"

    private const val MAX_LINES = 300
    private const val HINT = "split by responsibility (one job per file), not by line count"

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filterNot { it.path.endsWith("Previews.kt") }
        val violations = files.mapNotNull { file ->
            val lines = physicalLines(file.text)
            if (lines > MAX_LINES) Violation(file.path, lines, HINT) else null
        }
        return RuleResult(files.size, violations)
    }

    /** A final newline ends the last line; it does not start another one. */
    private fun physicalLines(text: String): Int =
        if (text.isEmpty()) 0 else text.count { it == '\n' } + if (text.endsWith('\n')) 0 else 1
}
