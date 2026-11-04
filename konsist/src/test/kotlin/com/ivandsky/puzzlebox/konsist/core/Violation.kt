package com.ivandsky.puzzlebox.konsist.core

/** One finding of one rule. path = project-relative (to the scanned root), '/'-separated.
 *  value = measured count for ceiling rules (S1, S2, S5), null for presence rules.
 *  A rule emits at most one Violation per path. */
data class Violation(val path: String, val value: Int? = null, val hint: String)
