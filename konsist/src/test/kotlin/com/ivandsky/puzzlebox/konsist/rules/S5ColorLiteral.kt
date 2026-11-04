package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S5ColorLiteral : Rule {
    override val id = "S5"

    private const val DESIGN_SYSTEM = "com.ivandsky.puzzlebox.designsystem"
    private const val HINT = "use a design-system color token"

    private const val NUMBER_ARG = """\(\s*(?:\w+\s*=\s*)?-?(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?)[fFlLuU]*"""

    /** The fully qualified call is Compose's whatever the imports say. */
    private val qualifiedColor = Regex("""(?<![\w.])androidx\.compose\.ui\.graphics\.Color\s*$NUMBER_ARG""")

    /** A bare `Color(` is Compose's only in a file that imports it; (?<![\w.]) keeps `MyColor(` and `x.Color(` out. */
    private val bareColor = Regex("""(?<![\w.])Color\s*$NUMBER_ARG""")
    private val composeImport = Regex("""(?m)^\s*import\s+androidx\.compose\.ui\.graphics\.(?:Color|\*)\s*$""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filterNot { it.isDesignSystem() }
        val violations = files.mapNotNull { file ->
            val code = file.codeText
            val bare = if (composeImport.containsMatchIn(code)) bareColor.findAll(code).count() else 0
            val count = qualifiedColor.findAll(code).count() + bare
            if (count > 0) Violation(file.path, count, HINT) else null
        }
        return RuleResult(files.size, violations)
    }

    private fun SourceFile.isDesignSystem() =
        packageName == DESIGN_SYSTEM || packageName.startsWith("$DESIGN_SYSTEM.")
}
