package com.ivandsky.puzzlebox.data.linkedin

/** What the Post to LinkedIn button of one game day says. Only [Ready], [Unknown] and [Offline] take a tap. */
sealed interface LinkedInPostState {
    /** No button: this solve cannot be posted, or posting is off. */
    data object Hidden : LinkedInPostState

    data object Ready : LinkedInPostState

    /** A post of this day is running; it goes on when the screen is left. */
    data object Sending : LinkedInPostState

    /** This phone posted the result. */
    data object Posted : LinkedInPostState

    /** LinkedIn already held a result for the day, so nothing was sent. */
    data object AlreadyOnLinkedIn : LinkedInPostState

    /** A post was sent and its answer never came, so it may or may not have landed; the next tap checks before it sends. */
    data object Unknown : LinkedInPostState

    /** LinkedIn could not be reached and nothing was sent. */
    data object Offline : LinkedInPostState

    /** The link is paused; nothing is sent until it is resumed. */
    data object Paused : LinkedInPostState

    /** The day is no longer the game's today, so nothing was sent. */
    data object NotToday : LinkedInPostState

    /** LinkedIn served the account its onboarding tutorial in place of the daily, so nothing was sent and nothing is paused. */
    data object Tutorial : LinkedInPostState
}
