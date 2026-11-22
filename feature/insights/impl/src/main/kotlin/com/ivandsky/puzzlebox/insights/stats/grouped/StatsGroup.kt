package com.ivandsky.puzzlebox.insights.stats.grouped

/** The four groups of the grouped stats page, in the owner's order, each with the [title] its sticky header says. */
internal enum class StatsGroup(val title: String) {
    Speed("Speed"),
    HintFree("Hint-free"),
    Routine("Routine"),
    Friends("Friends"),
}
