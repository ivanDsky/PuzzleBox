package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S12AppWindows : Rule {
    override val id = "S12"

    private const val HINT = "use AppPopup or AppDropdownMenu from core:designsystem; a raw popup window skips the Vibration gate"

    /** (?<!\w) keeps `AppPopup(` and `AppDropdownMenu(` out; a qualified `window.Popup(` still matches. */
    private val rawCall = Regex("""(?<!\w)(?:Popup|DropdownMenu|ExposedDropdownMenu)\s*\(""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filterNot { it.module.path == ":core:designsystem" }
        val violations = files.mapNotNull { file ->
            if (rawCall.containsMatchIn(file.codeText)) Violation(file.path, null, HINT) else null
        }
        return RuleResult(files.size, violations)
    }
}
