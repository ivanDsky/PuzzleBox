package com.ivandsky.puzzlebox.gameui

/**
 * What the result page's Post to LinkedIn button says, and whether it takes a tap. No button is a null state, not an
 * entry here. Only [Post], [Check] and [Retry] take a tap; the rest say where the post stands.
 */
enum class LinkedInPostButtonState(val label: String, val enabled: Boolean) {
    Post("Post to LinkedIn", enabled = true),
    Posting("Posting…", enabled = false),
    Posted("Posted to LinkedIn", enabled = false),
    AlreadyOnLinkedIn("Already on LinkedIn", enabled = false),

    /** A post's answer never came: a tap checks LinkedIn before it sends. */
    Check("Not sure it reached LinkedIn. Tap to check.", enabled = true),
    Retry("Couldn't reach LinkedIn. Try again.", enabled = true),
    Paused("LinkedIn paused — open Profile to resume", enabled = false),
    NotToday("That puzzle is no longer today's", enabled = false),
}
