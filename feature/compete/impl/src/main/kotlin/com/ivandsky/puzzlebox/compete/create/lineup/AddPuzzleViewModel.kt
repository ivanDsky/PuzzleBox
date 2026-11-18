package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.CodeLookup
import com.ivandsky.puzzlebox.domain.lineup.Freshness
import com.ivandsky.puzzlebox.domain.lineup.LoadPuzzleByCode
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The add sheet's exact-puzzle search: the text and chips the player set, the pickable puzzles they narrow, and which of
 * those the lineup already holds ([setAdded]). The list is read once while the sheet is on screen, however the query changes;
 * the daily archive comes a page at a time as [loadMore] asks.
 *
 * A text that is a share code or a puzzle link and matches nothing loaded is looked up with [lookup]: the state says so
 * while it runs, then holds the puzzle it found or the line of why there is none. The lookup is keyed by the code in the
 * text, so a changed text drops the old one and [reset] leaves nothing of it.
 */
internal class AddPuzzleViewModel(
    observe: (Flow<PickerQuery>, Flow<Int>) -> Flow<PickerResult>,
    lookup: suspend (String) -> CodeLookup,
) : ViewModel() {

    private val query = MutableStateFlow(NO_QUERY)
    private val added = MutableStateFlow<Set<LegRef>>(emptySet())
    private val archivePages = MutableStateFlow(1)

    // A link searches as the code it holds: only the code is in the lists.
    private val asked = query.map { q -> ShareCode.fromLink(q.text.trim())?.let { q.copy(text = it.value) } ?: q }

    private val list: StateFlow<AddPuzzleState> = combine(query, observe(asked, archivePages), added) { shown, result, held ->
        AddPuzzleState(shown, result.rows.map { PickerEntry(it, it.entry.ref in held) }, result.offline, result.moreDays, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddPuzzleState())

    /** A code is looked up only once the lists loaded and none of their rows answers the text. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val lookups: Flow<Lookup?> = list
        .map { if (it.loaded && it.rows.isEmpty()) LoadPuzzleByCode.codeIn(it.query.text) else null }
        .distinctUntilChanged()
        .flatMapLatest { code ->
            if (code == null) flowOf(null) else flow {
                emit(Lookup(code, outcome = null))
                emit(Lookup(code, lookup(code.value)))
            }
        }

    val state: StateFlow<AddPuzzleState> = combine(list, lookups, added) { base, looked, held ->
        // A lookup of another text's code, or beside rows that now answer, is not this state's.
        val current = looked?.takeIf { base.rows.isEmpty() && it.code == LoadPuzzleByCode.codeIn(base.query.text) }
        val found = (current?.outcome as? CodeLookup.Found)?.puzzle
        base.copy(
            rows = found?.let { listOf(PickerEntry(it, it.entry.ref in held)) } ?: base.rows,
            lookup = current?.let { lookupLine(it.code, it.outcome) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddPuzzleState())

    fun setText(text: String) = query.update { it.copy(text = text) }
    fun toggle(freshness: Freshness) = query.update { it.copy(fresh = it.fresh.toggled(freshness)) }
    fun toggle(source: PuzzleSource) = query.update { it.copy(sources = it.sources.toggled(source)) }
    fun toggle(game: GameId) = query.update { it.copy(games = it.games.toggled(game)) }

    /** The puzzles the lineup holds now. */
    fun setAdded(refs: Set<LegRef>) {
        added.value = refs
    }

    /** The list ran out while the player scrolled: read one more page of the daily archive, if it has one. */
    fun loadMore() {
        if (state.value.moreDays) archivePages.update { it + 1 }
    }

    /** The next opening of the sheet starts clean. */
    fun reset() {
        query.value = NO_QUERY
        archivePages.value = 1
    }

    /** One code's lookup: [outcome] is null while it runs. */
    private class Lookup(val code: ShareCode, val outcome: CodeLookup?)
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
