package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource

internal const val OFFLINE_NOTE = "You're offline. Code lookup needs a connection."

/** Where the puzzle came from, in the words of a result row and of the lineup row it becomes. */
internal fun ExactPuzzle.sourceLabel(): String = when (source) {
    PuzzleSource.Community -> "Community"
    PuzzleSource.Friend -> author?.let { "From $it" } ?: "From a friend"
    PuzzleSource.PastDay -> "Past day"
    PuzzleSource.Mine -> "Yours"
}

private val PickerQuery.filtered: Boolean get() = fresh.isNotEmpty() || sources.isNotEmpty() || games.isNotEmpty()

private val PickerQuery.narrowed: Boolean get() = text.isNotBlank() || filtered

/** The line over the results: how many match once the player narrowed them, else what the order is. */
internal fun resultsLine(query: PickerQuery, count: Int): String = if (query.narrowed) "$count found" else "Newest first"

/** What an empty result says, naming the text and whether chips are narrowing it. */
internal fun emptyLine(query: PickerQuery): String {
    val text = query.text.trim()
    val what = if (text.isEmpty()) "Nothing matches these filters" else "Nothing matches \"$text\"" + if (query.filtered) " with these filters" else ""
    return if (query.narrowed) "$what. Try a code, or clear the filters." else "No puzzles to pick from yet."
}
