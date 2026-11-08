package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S7NoSystemDarkTheme : Rule {
    override val id = "S7"

    private const val HINT = "read LocalDarkTheme.current (the resolved app theme), not the system night flag"

    /** (?<!\w) and (?!\w) keep names that merely contain an API name out. */
    private val systemNight = Regex("""(?<!\w)(?:isSystemInDarkTheme|UI_MODE_NIGHT_MASK)(?!\w)""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filterNot { it.mayReadSystemNight() }
        val violations = files.mapNotNull { file ->
            if (systemNight.containsMatchIn(file.codeText)) Violation(file.path, null, HINT) else null
        }
        return RuleResult(files.size, violations)
    }

    /**
     * The three places that turn the system flag into the app theme: the theme's default
     * argument, MainActivity's appearance resolution and the debug Showkase wrapper.
     */
    private fun SourceFile.mayReadSystemNight(): Boolean {
        val name = path.substringAfterLast('/')
        return when (module.path) {
            ":core:designsystem" -> name == "Theme.kt"
            ":app" -> name == "MainActivity.kt" || sourceSet == "debug"
            else -> false
        }
    }
}
