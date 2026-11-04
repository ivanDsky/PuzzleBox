package com.ivandsky.puzzlebox.konsist.core

/** A structure rule: a pure function of the tree. Verdicts are never decided here; RuleCheck sends them through judge. */
interface Rule {
    /** "S2", "P5": names the allowlist file and the ADR anchor. */
    val id: String

    /** Source-set kinds the rule scans; also the paths an allowlist entry may legitimately name. */
    val kinds: Set<SourceSetKind> get() = setOf(SourceSetKind.PRODUCTION)

    fun check(tree: SourceTree): RuleResult
}
