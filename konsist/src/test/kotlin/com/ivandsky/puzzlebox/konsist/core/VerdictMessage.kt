package com.ivandsky.puzzlebox.konsist.core

internal object VerdictMessage {
    fun format(ruleId: String, findings: List<Finding>): String {
        val header = "$ruleId: ${findings.size} finding(s) — " +
            "see docs/adr/0009-in-module-structure-rules.md#${ruleId.lowercase()}"
        val lines = findings
            .sortedWith(compareBy({ pathOf(it) }, { it.line ?: 0 }))
            .map { "  ${subject(it)} — ${it.text}; ${fix(ruleId, it)}" }
        return (listOf(header) + lines).joinToString("\n")
    }

    private fun pathOf(finding: Finding): String = when (finding) {
        is Finding.New -> finding.violation.path
        is Finding.Grown -> finding.entry.path
        is Finding.Slack -> finding.entry.path
        is Finding.Stale -> finding.entry.path
        is Finding.Malformed -> finding.raw.substringBefore(' ')
        is Finding.Duplicate -> finding.path
    }

    private fun subject(finding: Finding): String = when (finding) {
        is Finding.New -> pathOf(finding) + finding.violation.value?.let { " value $it vs ceiling none" }.orEmpty()
        is Finding.Grown -> "${pathOf(finding)} value ${finding.violation.value} vs ceiling ${finding.entry.ceiling}"
        is Finding.Slack -> "${pathOf(finding)} value ${finding.violation.value} vs ceiling ${finding.entry.ceiling}"
        else -> pathOf(finding)
    }

    private fun fix(ruleId: String, finding: Finding): String = when (finding) {
        is Finding.New -> "fix the code (${finding.violation.hint})"
        is Finding.Grown, is Finding.Slack, is Finding.Stale, is Finding.Malformed, is Finding.Duplicate ->
            "edit allowlists/$ruleId.txt line ${finding.line}"
    }
}
