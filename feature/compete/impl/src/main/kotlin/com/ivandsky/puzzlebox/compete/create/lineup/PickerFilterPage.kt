package com.ivandsky.puzzlebox.compete.create.lineup

/** The three option groups in chip order. Past you has no Freshness. */
internal enum class PickerFilterPage(val title: String, val allLabel: String, val tag: String) {
    Freshness("Freshness", "Any freshness", "duel.lineup.filter.freshness"),
    Source("Source", "All sources", "duel.lineup.filter.source"),
    Games("Games", "All games", "duel.lineup.filter.games"),
    ;

    val allTag: String get() = "$tag.all"
}
