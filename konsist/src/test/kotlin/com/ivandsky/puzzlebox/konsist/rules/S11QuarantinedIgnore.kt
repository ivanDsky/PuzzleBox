package com.ivandsky.puzzlebox.konsist.rules

import com.ivandsky.puzzlebox.konsist.core.Rule
import com.ivandsky.puzzlebox.konsist.core.RuleResult
import com.ivandsky.puzzlebox.konsist.core.Scan
import com.ivandsky.puzzlebox.konsist.core.SourceFile
import com.ivandsky.puzzlebox.konsist.core.SourceSetKind
import com.ivandsky.puzzlebox.konsist.core.SourceTree
import com.ivandsky.puzzlebox.konsist.core.Violation
import java.time.LocalDate

/**
 * Every ignored test is a dated quarantine with a ticket: `@Ignore("quarantine #<issue> until <YYYY-MM-DD>")`.
 * Why: a flaky test on main is quarantined in the same incident, for at most 48 hours (ADR-0023); an undated `@Ignore`
 * is a test nobody owns any more. The date is not compared with today, so an expired quarantine never turns main red.
 */
object S11QuarantinedIgnore : Rule {
    override val id = "S11"
    override val kinds = setOf(SourceSetKind.TEST)

    private const val HINT = "fix or delete the test, or quarantine it: @Ignore(\"quarantine #<issue> until <YYYY-MM-DD>\") (ADR-0023)"

    /** `@Ignore`, `@org.junit.Ignore` and any other qualified `Ignore`; `@IgnoredOnParcel` and a `return@Ignore` label are not. */
    private val ignore = Regex("""(?<!\w)@(?:\w+\s*\.\s*)*Ignore\b""")

    /** The whole argument list: one plain literal, positional or named `value`. */
    private val argument = Regex("""\(\s*(?:value\s*=\s*)?"([^"\\$]*)"\s*\)""")
    private val reason = Regex("""quarantine #\d+ until (\d{4}-\d{2}-\d{2})""")

    override fun check(tree: SourceTree): RuleResult {
        val files = tree.files(SourceSetKind.TEST)
        val violations = files.filter { file ->
            ignore.findAll(file.codeText).any { !file.quarantines(it.range.last + 1) }
        }.map { Violation(it.path, null, HINT) }
        return RuleResult(files.size, violations)
    }

    /** Balanced on codeText, where string contents are blank; the reason is read from strippedText at the same offsets. */
    private fun SourceFile.quarantines(afterName: Int): Boolean {
        val open = Scan.skipSpace(codeText, afterName)
        if (codeText.getOrNull(open) != '(') return false
        val end = Scan.closing(codeText, open) ?: return false
        val literal = argument.matchEntire(strippedText.substring(open, end)) ?: return false
        val date = reason.matchEntire(literal.groupValues[1]) ?: return false
        // ISO_LOCAL_DATE resolves strictly: 2026-02-30 is no date.
        return runCatching { LocalDate.parse(date.groupValues[1]) }.isSuccess
    }
}
