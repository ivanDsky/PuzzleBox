package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.Freshness
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The add sheet's exact-puzzle search: the text and chips the player set, the pickable puzzles they narrow, and which of
 * those the lineup already holds ([setAdded]). The list is read once while the sheet is on screen, however the query changes;
 * the daily archive comes a page at a time as [loadMore] asks.
 */
internal class AddPuzzleViewModel(observe: (Flow<PickerQuery>, Flow<Int>) -> Flow<PickerResult>) : ViewModel() {

    private val query = MutableStateFlow(NO_QUERY)
    private val added = MutableStateFlow<Set<LegRef>>(emptySet())
    private val archivePages = MutableStateFlow(1)

    val state: StateFlow<AddPuzzleState> = combine(query, observe(query, archivePages), added) { asked, result, held ->
        AddPuzzleState(asked, result.rows.map { PickerEntry(it, it.entry.ref in held) }, result.offline, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddPuzzleState())

    fun setText(text: String) = query.update { it.copy(text = text) }
    fun toggle(freshness: Freshness) = query.update { it.copy(fresh = it.fresh.toggled(freshness)) }
    fun toggle(source: PuzzleSource) = query.update { it.copy(sources = it.sources.toggled(source)) }
    fun toggle(game: GameId) = query.update { it.copy(games = it.games.toggled(game)) }

    /** The puzzles the lineup holds now. */
    fun setAdded(refs: Set<LegRef>) {
        added.value = refs
    }

    /** The list ran out while the player scrolled: read one more page of the daily archive. */
    fun loadMore() = archivePages.update { it + 1 }

    /** The next opening of the sheet starts clean. */
    fun reset() {
        query.value = NO_QUERY
        archivePages.value = 1
    }
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
