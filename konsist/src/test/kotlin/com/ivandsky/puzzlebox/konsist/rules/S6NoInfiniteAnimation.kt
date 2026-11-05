package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S6NoInfiniteAnimation : Rule {
    override val id = "S6"

    private const val HINT = "games animate only in response to input; use a finite animation"

    /** (?<!\w) and (?!\w) keep names that merely contain an API name (`myInfiniteRepeatable`) out. */
    private val infiniteApi =
        Regex("""(?<!\w)(?:rememberInfiniteTransition|infiniteRepeatable|withInfiniteAnimationFrame)(?!\w)""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filter { it.module.path.startsWith(":games:") }
        val violations = files.mapNotNull { file ->
            if (infiniteApi.containsMatchIn(file.codeText)) Violation(file.path, null, HINT) else null
        }
        return RuleResult(files.size, violations)
    }
}
