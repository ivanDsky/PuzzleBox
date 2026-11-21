package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S14NoSegmentedButtons : Rule {
    override val id = "S14"

    private const val HINT = "either/or choices on a work surface are ChoiceTiles or ChoiceRows from core:designsystem, not Material's segmented button"

    /** (?<!\w) and (?!\w) keep names that merely contain one (`MySegmentedButtonRow`) out. */
    private val segmented = Regex(
        """(?<!\w)(?:SingleChoice|MultiChoice)SegmentedButtonRow(?!\w)|(?<!\w)SegmentedButton(?:Defaults)?(?!\w)""",
    )

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION)
        val violations = files.mapNotNull { file ->
            if (segmented.containsMatchIn(file.codeText)) Violation(file.path, null, HINT) else null
        }
        return RuleResult(files.size, violations)
    }
}
