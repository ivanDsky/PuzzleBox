package com.ivandsky.puzzlebox.insights.stats

/** [count] of a [noun] as a person reads it: "1 solve", "5 solves". */
internal fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"
