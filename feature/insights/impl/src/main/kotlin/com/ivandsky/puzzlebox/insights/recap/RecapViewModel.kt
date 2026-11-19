package com.ivandsky.puzzlebox.insights.recap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.restoring
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecap
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile

/**
 * One recap story. It reads the cards once when the recap opens ([ObserveRecap]) and keeps them for the life of the
 * screen: a solve or a restore that lands mid-story never moves a page. The first cloud restore is the one exception:
 * an empty period waits it out and reads once more, so a story is not declared empty while the history is still coming
 * back (A23).
 *
 * The story opens on the local cards, which never wait for the network. The friends card is the one late arrival: when
 * the friends read answers, [ObserveRecap]'s second value is the same cards with that card added at the end, and the
 * story swaps it in. Pages already there do not move and the player keeps their place; only the progress strip gains a
 * step. A friends answer that adds no card changes nothing.
 *
 * Saved state holds the page the player is on, a position and never a number: after process death the facts are
 * computed again and the story reopens on that page, kept inside the cards and the Share page after them.
 */
internal class RecapViewModel(
    private val period: RecapPeriod,
    private val observeRecap: ObserveRecap,
    private val cloudSync: CloudSync,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val uiState: StateFlow<RecapUiState> = flow {
        var restoreWaited = false
        while (true) {
            var restoring = false
            observeRecap(period).transformWhile { cards ->
                restoring = !restoreWaited && cards.isEmpty() && cloudSync.restoring.first()
                if (!restoring) emit(cards)
                !restoring
            }.collect { emit(ready(it)) }
            if (!restoring) break
            emit(RecapUiState.Restoring)
            cloudSync.restoring.first { !it }
            restoreWaited = true
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, RecapUiState.Loading)

    /** The story reached the page at [index]; a reopened story starts there. */
    fun showPage(index: Int) {
        savedState[PAGE_KEY] = index
    }

    private fun ready(cards: List<RecapCard>): RecapUiState =
        if (cards.isEmpty()) {
            RecapUiState.Empty
        } else {
            val saved = savedState.get<Int>(PAGE_KEY) ?: 0
            RecapUiState.Ready(period, cards, initialPage = saved.coerceIn(0, cards.size))
        }

    private companion object {
        const val PAGE_KEY = "recap.page"
    }
}
