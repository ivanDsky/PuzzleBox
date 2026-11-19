package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.compete.domain.lineup.CodeLookup
import com.ivandsky.puzzlebox.compete.domain.lineup.Freshness
import com.ivandsky.puzzlebox.compete.domain.lineup.LoadPuzzleByCode
import com.ivandsky.puzzlebox.compete.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.compete.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

/** How long a typed link or dashed code rests before it is looked up, so keystrokes on the way do not each ask the network. */
internal const val LOOKUP_DEBOUNCE_MS = 400L

/**
 * The add sheet's exact-puzzle search: the text and chips the player set, the pickable puzzles they narrow, and which of
 * those the lineup already holds ([setAdded]). The list is read once while the sheet is on screen, however the query changes;
 * the daily archive comes a page at a time as [loadMore] asks.
 *
 * A link or a dashed code typed in that matches nothing loaded is looked up with [lookup] after a short rest; a bare six
 * letters that matches nothing could as well be a word, so it is looked up only when the player [paste]s it or asks with
 * [lookUp]. The state says so while it runs, then holds the puzzle it found or the line of why there is none. The lookup is
 * keyed by the text, so a changed text drops the old one and [reset] leaves nothing of it.
 *
 * Past you's search has no [lookup] (null): what it lists is on the device, so a code that is no row is only an empty list,
 * with no network, no "Looking up…" and no retry. [pastYou] tells the state so the sheet words it.
 */
internal class AddPuzzleViewModel(
    observe: (Flow<PickerQuery>, Flow<Int>) -> Flow<PickerResult>,
    lookup: (suspend (String) -> CodeLookup)?,
    private val pastYou: Boolean = false,
) : ViewModel() {

    private val query = MutableStateFlow(NO_QUERY)
    private val added = MutableStateFlow<Set<LegRef>>(emptySet())
    private val archivePages = MutableStateFlow(1)
    private val explicit = MutableStateFlow<Explicit?>(null)
    private var rounds = 0

    // A link searches as the code it holds: only the code is in the lists.
    private val asked = query.map { q -> ShareCode.fromLink(q.text.trim())?.let { q.copy(text = it.value) } ?: q }

    private val list: StateFlow<AddPuzzleState> = combine(query, observe(asked, archivePages), added) { shown, result, held ->
        AddPuzzleState(shown, result.rows.map { PickerEntry(it, it.entry.ref in held) }, result.offline, result.moreDays, loaded = true, pastYou = pastYou)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddPuzzleState(pastYou = pastYou))

    /** What to look up: once the lists loaded and none of their rows answers the text, the code it announces or the player asked for. */
    private val wants: Flow<Want?> = combine(list, explicit) { base, request ->
        val text = base.query.text
        when {
            !base.loaded || base.rows.isNotEmpty() -> null
            request?.text == text -> LoadPuzzleByCode.codeIn(text)?.let { Want(it, text, request.round, restMs = 0L) }
            else -> LoadPuzzleByCode.announcedIn(text)?.let { Want(it, text, round = 0, restMs = LOOKUP_DEBOUNCE_MS) }
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lookups: Flow<Lookup?> = wants.flatMapLatest { want ->
        if (want == null || lookup == null) flowOf(null) else flow {
            emit(Lookup(want, outcome = null))
            delay(want.restMs)
            emit(Lookup(want, lookup(want.code.value)))
        }
    }

    val state: StateFlow<AddPuzzleState> = combine(list, lookups, added) { base, looked, held ->
        // A lookup of another text, or beside rows that now answer, is not this state's.
        val current = looked?.takeIf { base.rows.isEmpty() && it.want.text == base.query.text }
        val found = (current?.outcome as? CodeLookup.Found)?.puzzle
        base.copy(
            rows = found?.let { listOf(PickerEntry(it, it.entry.ref in held)) } ?: base.rows,
            lookup = current?.let { lookupLine(it.want.code, it.outcome) },
            canRetry = current?.outcome == CodeLookup.Offline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddPuzzleState(pastYou = pastYou))

    fun setText(text: String) {
        explicit.value = null
        query.update { it.copy(text = text) }
    }

    /** The player pasted [text]: whatever code it holds is looked up now, if nothing loaded answers it. */
    fun paste(text: String) {
        setText(text)
        explicit.value = Explicit(text, ++rounds)
    }

    /** The player asked to search, or to retry an offline lookup: the text's code, if any, is looked up again now. */
    fun lookUp() {
        explicit.value = Explicit(query.value.text, ++rounds)
    }

    fun toggle(freshness: Freshness) = query.update { it.copy(fresh = it.fresh.toggled(freshness)) }
    fun toggle(source: PuzzleSource) = query.update { it.copy(sources = it.sources.toggled(source)) }
    fun toggle(game: GameId) = query.update { it.copy(games = it.games.toggled(game)) }

    /** Every chip off; the text stays. */
    fun clearFilters() = query.update { it.copy(fresh = emptySet(), sources = emptySet(), games = emptySet()) }

    /** The "All …" row of one group: that group's chips off, the others stay. */
    fun clearFreshness() = query.update { it.copy(fresh = emptySet()) }
    fun clearSources() = query.update { it.copy(sources = emptySet()) }
    fun clearGames() = query.update { it.copy(games = emptySet()) }

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
        explicit.value = null
        query.value = NO_QUERY
        archivePages.value = 1
    }

    /** A lookup the player asked for by hand: [round] tells a second ask of the same text from the first. */
    private class Explicit(val text: String, val round: Int)

    private data class Want(val code: ShareCode, val text: String, val round: Int, val restMs: Long)

    /** One want's lookup: [outcome] is null while it runs. */
    private class Lookup(val want: Want, val outcome: CodeLookup?)
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
