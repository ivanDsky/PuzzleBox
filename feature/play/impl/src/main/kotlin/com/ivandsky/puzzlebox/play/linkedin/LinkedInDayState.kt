package com.ivandsky.puzzlebox.play.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
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
 * One screen's LinkedIn data for the (game, day) it shows. Plain class with no scope of its own: its owner
 * collects [view], and that collection is the only thing that runs a sync, so a day nobody looks at asks
 * LinkedIn nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkedInDayState(
    private val observe: ObserveLinkedInDay,
    private val sync: SyncLinkedInDay,
    private val link: LinkedInLink,
) {
    data class Target(val game: GameId, val day: LocalDate)

    // Bumped to run the shown day again; the number itself means nothing.
    private val again = MutableStateFlow(0)

    // No replay: a Resume with nobody collecting is dropped, so it cannot lift the pause later behind the player's back.
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The view of [target]'s day, null while [target] is null. Collecting starts that day's one sync
     * (debounced by [SyncLinkedInDay]); [target] changing or the collector going away cancels both the
     * observation and a sync still waiting out its debounce. Nothing runs without a collector.
     */
    fun view(target: Flow<Target?>): Flow<LinkedInDayView?> = channelFlow {
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
        combine(target.distinctUntilChanged(), again) { day, _ -> day }
            .transformLatest { day ->
                shown.value = day
                if (day == null) {
                    emit(null)
                } else {
                    coroutineScope {
                        // Its answer is not read: the observed day already carries how its last sync ended.
                        launch { sync(day.game, day.day) }
                        observe(day.game, day.day).collect { emit(it) }
                    }
                }
            }
            .collect { send(it) }
    }

    /** The view of [target]'s day as the phone holds it. Calls nothing: another collector's [view] syncs the day. */
    fun observed(target: Target): Flow<LinkedInDayView> = observe(target.game, target.day)

    /** The user asked again (Retry): the same day, one more sync. Does nothing unless a collector is on a day. */
    fun retry() {
        again.update { it + 1 }
    }

    /** The user asked to Resume: clears the pause latch, then syncs the shown day once more. Does nothing unless a collector is on a day. */
    fun resume() {
        resumes.tryEmit(Unit)
    }
}
