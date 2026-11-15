package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostState
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.gameui.LinkedInPostButtonState
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The result page's Post to LinkedIn button for one (game, day): what it says and what a tap does.
 * `LinkedInPush` is read here and nowhere else on the page: off (or without the LinkedIn bindings, the three
 * parameters null) the day's post state is never observed and a tap does nothing. The state itself, a post's
 * progress included, is [ObserveLinkedInPost]'s. [scope] is the screen's; a post outlives it, since the door
 * owns the post and leaving only stops the wait for its answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ResultPostButton(
    private val scope: CoroutineScope,
    releaseFlags: ReleaseFlags?,
    observePost: ObserveLinkedInPost?,
    private val submitPost: SubmitLinkedInResult?,
    private val game: GameId,
    private val day: LocalDate,
) {

    /** The button, null for no button. Observed only while the screen collects it and the flag is on. */
    val state: StateFlow<LinkedInPostButtonState?> =
        if (releaseFlags == null || observePost == null) {
            MutableStateFlow(null)
        } else {
            releaseFlags.enabled
                .map { ReleaseFlag.LinkedInPush in it }
                .distinctUntilChanged()
                .flatMapLatest { on -> if (on) observePost(game, day).map(LinkedInPostState::button) else flowOf(null) }
                .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
        }

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
private fun LinkedInPostState.button(): LinkedInPostButtonState? = when (this) {
    LinkedInPostState.Hidden -> null
    LinkedInPostState.Ready -> LinkedInPostButtonState.Post
    LinkedInPostState.Sending -> LinkedInPostButtonState.Posting
    LinkedInPostState.Posted -> LinkedInPostButtonState.Posted
    LinkedInPostState.AlreadyOnLinkedIn -> LinkedInPostButtonState.AlreadyOnLinkedIn
    LinkedInPostState.Unknown -> LinkedInPostButtonState.Check
    LinkedInPostState.Offline -> LinkedInPostButtonState.Retry
    LinkedInPostState.Paused -> LinkedInPostButtonState.Paused
    LinkedInPostState.NotToday -> LinkedInPostButtonState.NotToday
}
