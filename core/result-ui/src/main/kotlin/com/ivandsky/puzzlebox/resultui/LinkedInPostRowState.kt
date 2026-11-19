package com.ivandsky.puzzlebox.resultui

/**
 * What the result page's Post to LinkedIn row says, and whether it takes a tap. No row is a null state, not an entry
 * here. Only [Post], [Check] and [Retry] take a tap; the rest say where the post stands. Each state has a short [title]
 * and a [subtitle] that carries the sentence; a subtitle that names the player's time takes it from the caller.
 */
enum class LinkedInPostRowState(val title: String, val enabled: Boolean) {
    Post("Post to LinkedIn", enabled = true),
    Posting("Posting to LinkedIn", enabled = false),
    Posted("Posted to LinkedIn", enabled = false),
    AlreadyOnLinkedIn("Already on LinkedIn", enabled = false),

    /** A post's answer never came: a tap checks LinkedIn before it sends. */
    Check("Check LinkedIn", enabled = true),
    Retry("Post again", enabled = true),
    Paused("LinkedIn paused", enabled = false),
    NotToday("Not today's puzzle", enabled = false),
    Tutorial("Tutorial on LinkedIn today", enabled = false);

    /** The line under the title. [time] is the formatted solve time; without it the lines say "your time". */
    fun subtitle(time: String?): String = when (this) {
        Post -> "${time ?: "Your time"} isn't on LinkedIn yet"
        Posting -> "Sending your ${time ?: "time"}…"
        Posted -> "Your ${time ?: "time"} is on LinkedIn"
        AlreadyOnLinkedIn -> "LinkedIn has a result for this puzzle"
        Check -> "Not sure it reached LinkedIn. Tap to check."
        Retry -> "Couldn't reach LinkedIn"
        Paused -> "Open Profile to resume"
        NotToday -> "Only today's puzzle can be posted"
        Tutorial -> "Today's puzzle can't be posted from this account"
    }
}
