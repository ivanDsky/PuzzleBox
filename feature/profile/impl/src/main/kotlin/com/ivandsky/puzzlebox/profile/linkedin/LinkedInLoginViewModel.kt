package com.ivandsky.puzzlebox.profile.linkedin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.domain.linkedin.CreateLinkedInLink
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val OFFLINE_MESSAGE = "No connection. Check your network and try again."
private const val REJECTED_MESSAGE = "LinkedIn didn't accept the check. Try again in a while."

/**
 * The LinkedIn login's state holder: the risk warning, then LinkedIn's login in a web view, then
 * one handshake through [createLink], which alone writes the link and only on success. Every
 * other outcome, backing out and process death leave the player not connected.
 *
 * One completed login sends one check. [step] is the guard: a check starts only by moving the
 * step to [Step.Checking], so a second page-finished or a Try again landing meanwhile starts
 * nothing. The session is only ever tested for presence; its value never reaches this class.
 */
internal class LinkedInLoginViewModel(
    private val createLink: CreateLinkedInLink,
    private val cookies: LinkedInSessionCookies,
    /** Whether cloud sync is on, so the risk warning says the connections' boards are saved to the player's account. */
    val savedToAccount: Boolean = false,
) : ViewModel() {

    sealed interface Step {
        /** Nothing loaded: no web view, no cookie read. */
        data object Warning : Step

        /** The web view is shown. */
        data object SignIn : Step

        /** The one handshake call is running. */
        data object Checking : Step
        data class Failed(val message: String) : Step
        data object Done : Step
    }

    private val current = MutableStateFlow<Step>(Step.Warning)
    val step: StateFlow<Step> = current.asStateFlow()

    // Events arrive on the main thread only, so a plain flag is enough to take one Accept.
    private var accepted = false

    /** The only way out of the warning. Cookies still in the web view from an earlier login skip the retyping. */
    fun onAccept() {
        if (accepted) return
        accepted = true
        viewModelScope.launch {
            if (cookies.current() == null) current.value = Step.SignIn else if (beginCheck(Step.Warning)) check()
        }
    }

    /**
     * A page of the login flow finished loading. Without a session nothing changes: 2FA, captcha
     * and "verify it's you" pages are ordinary pages, and the step stays put so the web view is
     * not rebuilt under the player.
     */
    fun onPageFinished() {
        if (current.value != Step.SignIn) return
        viewModelScope.launch {
            // Several pages can finish around one login; only the first to see the session checks.
            if (cookies.current() != null && beginCheck(Step.SignIn)) check()
        }
    }

    /** Try again after a failure: one more check per tap. */
    fun onRetry() {
        val failed = current.value as? Step.Failed ?: return
        if (beginCheck(failed)) viewModelScope.launch { check() }
    }

    private fun beginCheck(from: Step): Boolean = current.compareAndSet(from, Step.Checking)

    private suspend fun check() {
        current.value = when (val result = createLink()) {
            is LinkedInRead.Ok -> Step.Done
            LinkedInRead.Offline -> Step.Failed(OFFLINE_MESSAGE)
            // The cookies vanished between the read and the call.
            LinkedInRead.SignedOut -> Step.SignIn
            is LinkedInRead.Rejected -> when (result.kind) {
                LinkedInRead.Rejected.Kind.SessionExpired -> {
                    // A dead login must not be sent again: wipe it so the web view starts a fresh one.
                    cookies.clear()
                    Step.SignIn
                }
                LinkedInRead.Rejected.Kind.Challenge,
                LinkedInRead.Rejected.Kind.RateLimited,
                LinkedInRead.Rejected.Kind.Unexpected,
                -> Step.Failed(REJECTED_MESSAGE)
            }
        }
    }
}
