package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The result route's live facts. [isRecord] is the A4 verdict for THIS play — null while
 * the write is in flight/unknown, or when no play is live; [label] is null without one.
 */
data class ResultUiState(
    val isRecord: Boolean? = null,
    val writeFailed: Boolean = false,
    val label: String? = null,
)

/**
 * The result route's state holder: the frozen facts ride the nav key, the record badge
 * and the write-failure affordance come from the [PlaySessionStore]'s live play — the
 * record write is never awaited here, it completes async (F2 §7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModel(private val store: PlaySessionStore) : ViewModel() {

    val state: StateFlow<ResultUiState> = store.active
        .flatMapLatest { play -> play?.record?.map(::resultOf) ?: flowOf(ResultUiState()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.active.value?.record?.value?.let(::resultOf) ?: ResultUiState())

    /** "Play again": a fresh session of the SAME puzzle (A9) in the live play. */
    fun replay() {
        store.active.value?.replay()
    }

    fun retryRecordWrite() {
        store.active.value?.retryRecordWrite()
    }

    private fun resultOf(record: ActivePlay.Record) =
        ResultUiState(isRecord = record.badge, writeFailed = record.writeFailed, label = record.label)
}
