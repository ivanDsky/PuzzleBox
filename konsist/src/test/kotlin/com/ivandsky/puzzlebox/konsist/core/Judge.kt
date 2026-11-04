package com.ivandsky.puzzlebox.konsist.core

fun judge(ruleId: String, violations: List<Violation>, allowlist: Allowlist, scopePaths: Set<String>): Verdict {
    val findings = allowlist.problems.toMutableList()
    val byPath = violations.associateBy { it.path }
    val entryPaths = allowlist.entries.map { it.path }.toSet()

    violations.filter { it.path !in entryPaths }.forEach { findings += Finding.New(it) }

    for (entry in allowlist.entries) {
        if (entry.path !in scopePaths) {
            findings += Finding.Stale(entry, "path not in scope")
            continue
        }
        val violation = byPath[entry.path]
        if (violation == null) {
            findings += Finding.Stale(entry, "no longer violates")
            continue
        }
        findings += judgeEntry(entry, violation)
    }
    return Verdict(ruleId, findings)
}

private fun judgeEntry(entry: AllowlistEntry, violation: Violation): List<Finding> {
    val ceiling = entry.ceiling
    val value = violation.value
    return when {
        ceiling == null && value == null -> emptyList()
        ceiling != null && value == null ->
            listOf(Finding.Malformed(entry.line, "${entry.path} $ceiling", "presence rule entry must not have a ceiling"))
        ceiling == null ->
            listOf(Finding.Malformed(entry.line, entry.path, "ceiling rule entry needs a ceiling"))
        value!! > ceiling -> listOf(Finding.Grown(entry, violation))
        value < ceiling -> listOf(Finding.Slack(entry, violation))
        else -> emptyList()
    }
}
