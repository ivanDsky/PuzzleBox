package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S10AppModalBottomSheet : Rule {
    override val id = "S10"

    private const val HINT = "use AppModalBottomSheet from core:designsystem; Material's ModalBottomSheet lets a flung list kick the sheet"

    /** (?<!\w) keeps `AppModalBottomSheet(` out; a qualified `material3.ModalBottomSheet(` still matches. */
    private val rawCall = Regex("""(?<!\w)ModalBottomSheet\s*\(""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filterNot { it.module.path == ":core:designsystem" }
        val violations = files.mapNotNull { file ->
            if (rawCall.containsMatchIn(file.codeText)) Violation(file.path, null, HINT) else null
        }
        return RuleResult(files.size, violations)
    }
}
