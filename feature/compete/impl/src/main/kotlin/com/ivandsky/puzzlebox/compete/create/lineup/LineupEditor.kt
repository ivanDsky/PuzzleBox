package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource

/**
 * The lineup editor's state, held by the New duel view model so it outlives a rotation: the [draft], the row the picker
 * page works on ([target]: a row's index to change it, the draft's size to add one; null while closed), the row that
 * just changed ([flashed]) and the row Create refused ([marked]), all 0-based.
 * [pinned] holds the puzzles a Create attempt drew: a retry re-sends them rather than drawing again, and any edit drops them.
 */
internal data class LineupEditor(
    val draft: LineupDraft = LineupDraft(emptyList()),
    val target: Int? = null,
    val flashed: Int? = null,
    val marked: Int? = null,
    private val pinned: List<LineupEntry>? = null,
) {
    /** What Create sends. */
    val entries: List<LineupEntry> get() = pinned ?: draft.entries
    val changing: Boolean get() = target != null && target < draft.size

    fun openAdd(): LineupEditor = if (draft.canAdd) copy(target = draft.size) else this
    fun openChange(index: Int): LineupEditor = if (index in draft.entries.indices) copy(target = index) else this
    fun dismiss(): LineupEditor = copy(target = null)

    /** Puts [entry] in the row the picker works on, closes the picker and flashes the row. */
    fun pick(entry: LineupEntry): LineupEditor {
        val row = target ?: return this
        val next = if (row < draft.size) draft.replaced(row, entry) else draft.added(entry)
        return copy(draft = next, target = null, flashed = row.takeIf { it < next.size }, marked = null, pinned = null)
    }

    fun remove(index: Int): LineupEditor = copy(draft = draft.removed(index), target = null, flashed = null, marked = null, pinned = null)
    fun flashDone(): LineupEditor = copy(flashed = null)

    /** Moves the puzzle at [from] to [to], which only reorders the rows; nothing else about them changes. */
    fun moved(from: Int, to: Int): LineupEditor {
        val next = draft.moved(from, to)
        return if (next == draft) this else copy(draft = next, target = null, flashed = null, marked = null, pinned = null)
    }

    /** Create refused the row at [position], which counts from 1. */
    fun refused(position: Int): LineupEditor = copy(marked = position - 1)

    fun unmarked(): LineupEditor = if (marked == null) this else copy(marked = null)

    fun pin(legs: List<MatchLeg>): LineupEditor = copy(pinned = legs.map(MatchLeg::asExact))

    /** The pinned puzzles are dropped, as after a change to what a draw depends on. */
    fun unpinned(): LineupEditor = copy(pinned = null)

    companion object {
        fun opening(available: List<DuelGame>) = LineupEditor(LineupDraft.opening(available))
    }
}

/** A drawn leg as the exact entry Create passes through; it uses only the leg's reference and author. */
private fun MatchLeg.asExact() = ExactPuzzle(
    ref = ref,
    authorUid = authorUid,
    title = ref.gameId.value,
    author = null,
    source = if (authorUid == null) PuzzleSource.PastDay else PuzzleSource.Friend,
)
