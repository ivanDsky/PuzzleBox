package com.ivandsky.puzzlebox.konsist.core

data class Verdict(val ruleId: String, val findings: List<Finding>) {
    val ok: Boolean get() = findings.isEmpty()

    fun message(): String = VerdictMessage.format(ruleId, findings)
}
