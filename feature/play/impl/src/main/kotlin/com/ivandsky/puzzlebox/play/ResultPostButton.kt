package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostView
import com.ivandsky.puzzlebox.play.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.play.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.LinkedInPostRowState
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The result page's Post to LinkedIn button for one (game, day): what it says and what a tap does.
 * Without the LinkedIn bindings (the two use cases null) the day's post state is never observed and a tap does
 * nothing. The state itself, a post's
 * progress included, is [ObserveLinkedInPost]'s. [scope] is the screen's; a post outlives it, since the door
 * owns the post and leaving only stops the wait for its answer.
 */
internal class ResultPostButton(
    private val scope: CoroutineScope,
    observePost: ObserveLinkedInPost?,
    private val submitPost: SubmitLinkedInResult?,
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
        if (state.value?.enabled != true || posting?.isActive == true) return
        posting = scope.launch { submit(game, day) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
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
