package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation

object S8CopyVocabulary : Rule {
    override val id = "S8"

    private const val HINT = "use the canonical word: color, favorite, Ranks, queen, puzzles, this device"

    /** `(?<![A-Za-z$])` keeps `surrounds`, `grounds` and a template identifier like `$crowns` out; the open end keeps plurals and `colourful` in. */
    private val banned = Regex("""(?<![A-Za-z$])(?:colour|favourite|leaderboard|crown|this phone|rounds)""", RegexOption.IGNORE_CASE)

    /** A lowercase dotted or snake key (`"leaderboards"`, `"duel.rounds.$quick"`) is a name, not copy. */
    private val machineKey = Regex("""[a-z0-9_.\-{}$]+""")

    /** Literals passed to these calls are never shown to the player. */
    private val silentCalls = setOf(
        "Log.v", "Log.d", "Log.i", "Log.w", "Log.e", "Log.wtf", "println", "print",
        "require", "requireNotNull", "check", "checkNotNull", "error", "testTag", "Preview",
        "malformed",
    )
    private val logTag = Regex("""\bTAG\s*=\s*$""")
    private val callName = Regex("""[\w.]+$""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.PRODUCTION)
        val violations = files.mapNotNull { file ->
            val count = file.visibleBannedWords()
            if (count > 0) Violation(file.path, count, HINT) else null
        }
        return RuleResult(files.size, violations)
    }

    /** A hit is inside a string literal when the code-only text blanked it but the comment-free text did not. */
    private fun SourceFile.visibleBannedWords(): Int = banned.findAll(strippedText).count { hit ->
        val at = hit.range.first
        codeText[at] == ' ' && strippedText[at] != ' ' &&
            !isMachineKey(at) && !isLogTag(at) && enclosingCall(at) !in silentCalls
    }

    /** `const val TAG = "LeaderboardSubmit"`: a log tag is a name, not copy. */
    private fun SourceFile.isLogTag(at: Int): Boolean {
        val quote = strippedText.lastIndexOf('"', at)
        return quote > 0 && logTag.containsMatchIn(strippedText.substring(0, quote).trimEnd())
    }

    private fun SourceFile.isMachineKey(at: Int): Boolean {
        var from = at
        while (from > 0 && strippedText[from - 1].let { it != '"' && it != '\n' }) from--
        var to = at
        while (to < strippedText.length && strippedText[to].let { it != '"' && it != '\n' }) to++
        return machineKey.matches(strippedText.substring(from, to))
    }

    /** Name of the nearest call whose parentheses (or trailing lambda) hold [at]; null when none. */
    private fun SourceFile.enclosingCall(at: Int): String? {
        var depth = 0
        var i = at - 1
        while (i >= 0) {
            when (codeText[i]) {
                ')', ']', '}' -> depth++
                '(', '[', '{' -> if (depth > 0) depth-- else {
                    val name = if (codeText[i] == '{') lambdaOwner(i) else nameBefore(i)
                    if (name != null) return name
                }
            }
            i--
        }
        return null
    }

    private fun SourceFile.nameBefore(open: Int): String? =
        callName.find(codeText.substring(0, open).trimEnd())?.value

    /** `require(x) { "…" }`: the lambda belongs to the call whose argument list ends right before it. */
    private fun SourceFile.lambdaOwner(open: Int): String? {
        var i = open - 1
        while (i >= 0 && codeText[i].isWhitespace()) i--
        if (i < 0 || codeText[i] != ')') return null
        var depth = 0
        while (i >= 0) {
            if (codeText[i] == ')') depth++
            if (codeText[i] == '(' && --depth == 0) return nameBefore(i)
            i--
        }
        return null
    }
}
