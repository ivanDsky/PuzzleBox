package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry

/** The ordered puzzles of a duel being built: one to [Match.MAX_LEGS]. Pure; every edit returns a new draft, and one that can't apply returns this. */
internal data class LineupDraft(val entries: List<LineupEntry>) {
    val size: Int get() = entries.size
    val canAdd: Boolean get() = size < Match.MAX_LEGS

    /** The last puzzle stays: a duel is never empty. */
    val canRemove: Boolean get() = size > MIN_ENTRIES

    fun added(entry: LineupEntry): LineupDraft = if (canAdd) copy(entries = entries + entry) else this

    fun replaced(index: Int, entry: LineupEntry): LineupDraft =
        if (index in entries.indices) copy(entries = entries.toMutableList().also { it[index] = entry }) else this

    fun removed(index: Int): LineupDraft =
        if (canRemove && index in entries.indices) copy(entries = entries.filterIndexed { at, _ -> at != index }) else this

    companion object {
        const val MIN_ENTRIES = 1
        const val OPENING_ENTRIES = 3

        /** What Quick draws, as rows: three "Any", one per game in turn; empty until [available] is known. */
        fun opening(available: List<DuelGame>): LineupDraft =
            LineupDraft(if (available.isEmpty()) emptyList() else List(OPENING_ENTRIES) { AnyGame(available[it % available.size].id) })
    }
}
