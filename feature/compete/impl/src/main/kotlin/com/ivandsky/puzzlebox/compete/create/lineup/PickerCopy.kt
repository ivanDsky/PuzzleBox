package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.domain.lineup.CodeLookup
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.ShareCode

internal const val OFFLINE_NOTE = "You're offline. Code lookup needs a connection."

/** Where the puzzle came from, in the words of a result row and of the lineup row it becomes. */
internal fun ExactPuzzle.sourceLabel(): String = when (source) {
    PuzzleSource.Community -> "Community"
    PuzzleSource.Friend -> author?.let { "From $it" } ?: "From a friend"
    PuzzleSource.PastDay -> "Past day"
    PuzzleSource.Mine -> "Yours"
}

/** What a code lookup says while it runs or when it ended without a puzzle (A20); [CodeLookup.Found] and [CodeLookup.Invalid] say nothing. */
internal fun lookupLine(code: ShareCode, outcome: CodeLookup?): String? = when (outcome) {
    null -> "Looking up ${code.display}…"
    CodeLookup.NotFound -> "No puzzle has the code ${code.display}. Check it with the person who shared it."
    CodeLookup.NoLongerAvailable -> "The person who shared ${code.display} took that puzzle down."
    CodeLookup.CannotOpen -> "${code.display} can't be opened in this version of the app. Update PuzzleBox to try it."
    CodeLookup.CheckFailed -> "This puzzle can't be opened (${code.display})."
    CodeLookup.Offline -> OFFLINE_NOTE
    is CodeLookup.Found, CodeLookup.Invalid -> null
}

private val PickerQuery.filtered: Boolean get() = fresh.isNotEmpty() || sources.isNotEmpty() || games.isNotEmpty()

private val PickerQuery.narrowed: Boolean get() = text.isNotBlank() || filtered

/** The line over the results: how many puzzles match, with what the order is after the count when nothing narrows them. */
internal fun resultsLine(query: PickerQuery, count: Int, pastYou: Boolean = false): String {
    val counted = if (count == 1) "1 puzzle" else "$count puzzles"
    return when {
        query.narrowed -> counted
        pastYou -> "$counted · Recently solved first"
        else -> "$counted · Newest first"
    }
}

/** What an empty result says, naming the text and whether chips are narrowing it; Past you's says why it is only what was solved, since a code can't add to it. */
internal fun emptyLine(query: PickerQuery, pastYou: Boolean = false): String {
    if (!query.narrowed) return if (pastYou) "You haven't solved any puzzles yet." else "No puzzles to pick from yet."
    val text = query.text.trim()
    val what = if (text.isEmpty()) "Nothing matches these filters" else "Nothing matches \"$text\"" + if (query.filtered) " with these filters" else ""
    return if (pastYou) "$what. Past you only has puzzles you've solved." else "$what. Try a code, or clear the filters."
}
