package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S13VibratorBehindGate : Rule {
    override val id = "S13"

    /** The one file that plays Vibrator effects; `VibrationGate` wraps what it builds (#1686). */
    private const val GATE = "app/src/main/kotlin/com/ivandsky/puzzlebox/app/VibratorHapticFeedback.kt"

    private const val HINT = "play haptics through LocalHapticFeedback; the Vibrator is played only in VibratorHapticFeedback, behind the Vibration gate"

    /**
     * A `vibrate(` call, a `VibratorManager`, a service constant, or a `getSystemService` that takes the Vibrator class.
     * (?<!\w) and (?!\w) keep names that merely contain one out; `VibrationEffect` alone plays nothing, so it is not matched.
     */
    private val playPath = Regex(
        """(?<!\w)vibrate\s*\(|(?<!\w)VibratorManager(?!\w)|(?<!\w)VIBRATOR(?:_MANAGER)?_SERVICE(?!\w)|""" +
            """(?<!\w)getSystemService\s*\([^)]*?(?<!\w)Vibrator\s*::""",
    )

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION).filterNot { it.path == GATE }
        val violations = files.mapNotNull { file ->
            if (playPath.containsMatchIn(file.codeText)) Violation(file.path, null, HINT) else null
        }
        return RuleResult(files.size, violations)
    }
}
