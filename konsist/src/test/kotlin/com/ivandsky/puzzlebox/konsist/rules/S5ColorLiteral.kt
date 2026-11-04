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

    // (?<!\w) keeps MyColor( out while still matching a qualified androidx...graphics.Color(.
    private val literalColor = Regex(
        """(?<!\w)Color\(\s*(?:\w+\s*=\s*)?-?(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?)[fFlLuU]*""",
    )

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filterNot { it.isDesignSystem() }
        val violations = files.mapNotNull { file ->
            val count = literalColor.findAll(file.strippedText).count()
            if (count > 0) Violation(file.path, count, HINT) else null
        }
        return RuleResult(files.size, violations)
    }

    private fun SourceFile.isDesignSystem() =
        packageName == DESIGN_SYSTEM || packageName.startsWith("$DESIGN_SYSTEM.")
}
