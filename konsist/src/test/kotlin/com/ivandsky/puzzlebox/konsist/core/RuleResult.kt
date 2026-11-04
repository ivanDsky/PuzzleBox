package com.ivandsky.puzzlebox.konsist.core

/** subjects = how many things the rule examined (files, classes, composables); 0 = scope empty. */
data class RuleResult(val subjects: Int, val violations: List<Violation>)
