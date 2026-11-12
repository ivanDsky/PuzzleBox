package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One screen's LinkedIn data for the (game, day) it shows: observing it, syncing it once it has stayed, and the
 * Retry and Resume the day card offers. Plain class with no scope of its own: its owner collects the flow [invoke] returns, and that
 * collection is the only thing that runs a sync, so a day nobody looks at asks LinkedIn nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLinkedInDayView(
    private val observe: ObserveLinkedInDay,
    private val sync: SyncLinkedInDay,
    private val link: LinkedInLink,
    private val days: LinkedInDays,
) {
    data class Target(val game: GameId, val day: LocalDate)

    // Bumped to run the shown day again; the number itself means nothing.
    private val again = MutableStateFlow(0)

    // No re-delivery: a Resume with nobody collecting is dropped, so it cannot lift the pause later behind the player's back.
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The view of [target]'s day, null while [target] is null. Collecting starts that day's one sync
     * (debounced by [SyncLinkedInDay]); [target] changing or the collector going away cancels both the
     * observation and a sync still waiting out its debounce. Nothing runs without a collector.
     */
    operator fun invoke(target: Flow<Target?>): Flow<LinkedInDayView?> = channelFlow {
        val shown = MutableStateFlow<Target?>(null)
        launch {
            resumes.collect {
                // The pause lifts only for a day on screen, so the sync it allows is that day's and nothing else's.
                if (shown.value != null) {
                    link.resume()
                    again.update { it + 1 }
                }
            }
        }
        var previous: Target? = null
        combine(target.distinctUntilChanged(), again) { day, _ -> day }
            .transformLatest { day ->
                shown.value = day
                // The old day's view is dropped at once: the store answers the new day a moment later, and until
                // then the card would show the last day's state as this day's. A retry keeps its day on screen.
                if (day == null || (previous != null && day != previous)) emit(null)
                previous = day
                if (day != null) {
                    // Until its sync has answered, an unsettled day is one that is about to be read. The store
                    // alone cannot say so: it reports the sync only once the debounce is over. A day the sync
                    // will not read (not a LinkedIn game, a future day, no puzzle number) is settled from the start.
                    val awaitingSync = MutableStateFlow(days.willRead(day.game, day.day))
                    coroutineScope {
                        // Its answer is not read: the observed day already carries how its last sync ended.
                        launch {
                            try {
                                sync(day.game, day.day)
                            } finally {
                                awaitingSync.value = false
                            }
                        }
                        observe(day.game, day.day)
                            .combine(awaitingSync) { view, waiting -> if (waiting) view.syncing() else view }
                            .distinctUntilChanged()
                            .collect { emit(it) }
                    }
                }
            }
            .collect { send(it) }
    }

    /** The view of [target]'s day as the phone holds it. Calls nothing: another collector's [invoke] syncs the day. */
    fun observed(target: Target): Flow<LinkedInDayView> = observe(target.game, target.day)

    /**
     * The days of [game] that have a saved board, for the archive calendar's dots (spec A25): one list per game, not a
     * query per day. Reads what the phone holds and calls nothing.
     */
    fun savedDays(game: GameId): Flow<Set<LocalDate>> = days.observeSavedDays(game)

    /** The user asked again (Retry): the same day, one more sync. Does nothing unless a collector is on a day. */
    fun retry() {
        again.update { it + 1 }
    }

    /** The user asked to Resume: clears the pause latch, then syncs the shown day once more. Does nothing unless a collector is on a day. */
    fun resume() {
        resumes.tryEmit(Unit)
    }
}

/**
 * A day with nothing to say yet (Idle) or whose last read failed (Offline) is read again by the sync that is
 * about to run, so it says so. Every other state is already the day's answer and is kept.
 */
private fun LinkedInDayView.syncing(): LinkedInDayView = when (status) {
    LinkedInDayStatus.Idle, LinkedInDayStatus.Offline -> copy(status = LinkedInDayStatus.Loading)
    else -> this
}
