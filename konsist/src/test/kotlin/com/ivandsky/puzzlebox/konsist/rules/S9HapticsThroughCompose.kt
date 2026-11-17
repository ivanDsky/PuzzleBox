package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S9HapticsThroughCompose : Rule {
    override val id = "S9"

    private const val HINT = "play haptics through LocalHapticFeedback, where the Vibration switch gates them"

    /** `HapticFeedbackConstants` or androidx's `HapticFeedbackConstantsCompat` as an import or a use; (?<!\w) and (?!\w) keep names that merely contain it out. */
    private val constants = Regex("""(?<!\w)HapticFeedbackConstants(?:Compat)?(?!\w)""")

    /** The View call: on a view receiver, or with a number, which Compose's typed `performHapticFeedback` never takes. */
    private val viewCall = Regex(
        """(?:(?<![\w.])view|LocalView\.current)\s*\??\.\s*performHapticFeedback(?!\w)|(?<!\w)performHapticFeedback\s*\(\s*-?\d""",
    )

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION)
        val violations = files.mapNotNull { file ->
            val code = file.codeText
            if (constants.containsMatchIn(code) || viewCall.containsMatchIn(code)) Violation(file.path, null, HINT) else null
        }
        return RuleResult(files.size, violations)
    }
}
