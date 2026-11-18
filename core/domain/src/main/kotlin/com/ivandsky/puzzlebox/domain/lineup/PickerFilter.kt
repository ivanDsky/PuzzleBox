package com.ivandsky.puzzlebox.domain.lineup

/** Whether the row survives the add sheet's [query]: text over title, author and code, then the chip groups (AND across, OR within). */
internal fun PickablePuzzle.passes(query: PickerQuery): Boolean =
    matches(query.text) &&
        (query.fresh.isEmpty() || Freshness.New in query.fresh && isNew || Freshness.NotPlayed in query.fresh && !played) &&
        (query.sources.isEmpty() || entry.source in query.sources) &&
        (query.games.isEmpty() || entry.gameId in query.games)

private fun PickablePuzzle.matches(text: String): Boolean {
    val needle = text.trim()
    if (needle.isEmpty()) return true
    return listOfNotNull(entry.title, entry.author, code?.value, code?.display).any { it.contains(needle, ignoreCase = true) }
}
