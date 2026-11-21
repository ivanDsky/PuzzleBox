package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.play.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.play.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.LinkedInPostRowState
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The result page's Post to LinkedIn button for one (game, day): what it says and what a tap does.
 * Without the LinkedIn bindings (the use cases null) the day's post state is never observed and a tap does
 * nothing. The state itself, a post's progress included, is [ObserveLinkedInPost]'s, and it never asks LinkedIn:
 * the phone only learns that LinkedIn already has the day's result by syncing the day. A page opened cold (the
 * widget, the Today tile) has not synced it, so a Post the door offers is [pending] until [dayView] has answered.
 * [scope] is the screen's; a post outlives it, since the door owns the post and leaving only stops the wait for its
 * answer.
 */
internal class ResultPostButton(
    private val scope: CoroutineScope,
    observePost: ObserveLinkedInPost?,
    private val submitPost: SubmitLinkedInResult?,
    dayView: ObserveLinkedInDayView?,
    private val game: GameId,
    private val day: LocalDate,
) {

    /** What the door shows, null for no button. Observed only while the screen collects it. */
    private val view: StateFlow<LinkedInPostView?> =
        if (observePost == null) {
            MutableStateFlow(null)
        } else {
            observePost(game, day).stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
        }

    /** The button, null for no button. */
    val state: StateFlow<LinkedInPostRowState?> =
        view.map { it?.state?.button() }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Latched when LinkedIn has answered for the day or the cap has passed, so a page that comes back from the background never holds again. */
    private val answered = MutableStateFlow(dayView == null)

    /**
     * The row is in its slot but not yet to be shown or tapped: it is Post, or not known yet, and LinkedIn has not
     * said whether it already holds the day's result. Collecting it is what asks LinkedIn (see [ask]), so the screen
     * collects it beside [state]. It follows [state] rather than the door, so it can lag the row but never lead it:
     * Post is never shown before this has held it. True until the first answer.
     */
    val pending: StateFlow<Boolean> =
        if (dayView == null || observePost == null) {
            MutableStateFlow(false)
        } else {
            channelFlow {
                if (!answered.value) launch { ask(dayView) }
                combine(state, answered) { row, done -> !done && (row == null || row == LinkedInPostRowState.Post) }
                    .collect { send(it) }
            }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)
        }

    /**
     * Syncs the day as soon as the door offers Post, with no debounce (the page's day is never scrubbed past). The
     * hold lifts when the player's own result is known absent, when the sync ends without finding it, or at
     * [HOLD_CAP_MS], since the call budget can park a sync far longer. A result found does not lift it: the door turns
     * that into AlreadyOnLinkedIn next, and Post must not show in between. The sync outlives the lift, so a result it
     * finds after the cap still turns the row. [dayView] is observed, never collected, so no other sync runs.
     */
    private suspend fun ask(dayView: ObserveLinkedInDayView) = coroutineScope {
        state.first { it == LinkedInPostRowState.Post }
        val target = ObserveLinkedInDayView.Target(game, day)
        val synced = MutableStateFlow(false)
        launch { delay(HOLD_CAP_MS); answered.value = true }
        launch { dayView.refresh(target); synced.value = true }
        combine(dayView.observed(target), synced) { own, done ->
            own.status == LinkedInDayStatus.DidNotPlay || (done && own.status !is LinkedInDayStatus.Imported)
        }.first { it }
        answered.value = true
    }

    /** The time the button names: the day's record row, the solve a post sends and LinkedIn holds. Null while the day has none. */
    val timeMs: StateFlow<Long?> =
        view.map { it?.timeMs }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private var posting: Job? = null

    /**
     * Asks [SubmitLinkedInResult] to post the day. Only a state that takes a tap does, and never while an earlier
     * tap's call is still running, so a double tap before the button says Posting is still one post. The answer is
     * not read here; the button follows [state].
     */
    fun tap() {
        val submit = submitPost ?: return
        if (state.value?.enabled != true || pending.value || posting?.isActive == true) return
        posting = scope.launch { submit(game, day) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** How long Post waits for LinkedIn: one own-result call fits well inside, and a stalled one never withholds Post for long. */
        const val HOLD_CAP_MS = 3_000L
    }
}

/** The button each post state shows; Hidden has none. */
private fun LinkedInPostState.button(): LinkedInPostRowState? = when (this) {
    LinkedInPostState.Hidden -> null
    LinkedInPostState.Ready -> LinkedInPostRowState.Post
    LinkedInPostState.Sending -> LinkedInPostRowState.Posting
    LinkedInPostState.Posted -> LinkedInPostRowState.Posted
    LinkedInPostState.AlreadyOnLinkedIn -> LinkedInPostRowState.AlreadyOnLinkedIn
    LinkedInPostState.Unknown -> LinkedInPostRowState.Check
    LinkedInPostState.Offline -> LinkedInPostRowState.Retry
    LinkedInPostState.Paused -> LinkedInPostRowState.Paused
    LinkedInPostState.NotToday -> LinkedInPostRowState.NotToday
    LinkedInPostState.Tutorial -> LinkedInPostRowState.Tutorial
}
