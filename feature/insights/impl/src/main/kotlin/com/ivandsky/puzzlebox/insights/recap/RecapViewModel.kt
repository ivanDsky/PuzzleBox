package com.ivandsky.puzzlebox.insights.recap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.restoring
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecap
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update

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
 * A failed local history read ends in [RecapUiState.Error] instead of a crash, and [retry] reads again from [RecapUiState.Loading].
 * A failure after the story has opened is logged and leaves the story as it is. Friends, provider and share failures never
 * get here: they stay on their own card.
 *
 * The title every card carries is read from the puzzle day when the story is ready ([RecapUiState.Ready.title]).
 *
 * Saved state holds the page the player is on, a position and never a number: after process death the facts are
 * computed again and the story reopens on that page, kept inside the cards and the Share page after them.
 */
internal class RecapViewModel(
    private val period: RecapPeriod,
    private val observeRecap: ObserveRecap,
    private val cloudSync: CloudSync,
    private val puzzleDay: GetPuzzleDay,
    private val savedState: SavedStateHandle,
    private val failures: FailureLog = LogcatFailureLog,
) : ViewModel() {

    private val reads = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RecapUiState> = reads.flatMapLatest { read() }.stateIn(viewModelScope, SharingStarted.Lazily, RecapUiState.Loading)

    /** Reads the history again after an [RecapUiState.Error], showing Loading meanwhile; the saved page is kept. */
    fun retry() = reads.update { it + 1 }

    private fun read(): Flow<RecapUiState> = flow {
        emit(RecapUiState.Loading)
        var opened = false
        try {
            readCards { state ->
                opened = opened || state is RecapUiState.Ready
                emit(state)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failures.warn(TAG, "recap history read", e)
            if (!opened) emit(RecapUiState.Error)
        }
    }

    private suspend fun readCards(publish: suspend (RecapUiState) -> Unit) {
        var restoreWaited = false
        while (true) {
            var restoring = false
            observeRecap(period).transformWhile { cards ->
                restoring = !restoreWaited && cards.isEmpty() && cloudSync.restoring.first()
                if (!restoring) emit(cards)
                !restoring
            }.collect { publish(ready(it)) }
            if (!restoring) break
            publish(RecapUiState.Restoring)
            cloudSync.restoring.first { !it }
            restoreWaited = true
        }
    }

    /** The story reached the page at [index]; a reopened story starts there. */
    fun showPage(index: Int) {
        savedState[PAGE_KEY] = index
    }

    private fun ready(cards: List<RecapCard>): RecapUiState =
        if (cards.isEmpty()) {
            RecapUiState.Empty
        } else {
            val saved = savedState.get<Int>(PAGE_KEY) ?: 0
            val today = puzzleDay()
            val stamped = cards.map { if (it is RecapCard.Streak) it.copy(today = today) else it }
            RecapUiState.Ready(period, period.title(today), stamped, initialPage = saved.coerceIn(0, cards.size))
        }

    private companion object {
        const val PAGE_KEY = "recap.page"
        const val TAG = "RecapViewModel"
    }
}
