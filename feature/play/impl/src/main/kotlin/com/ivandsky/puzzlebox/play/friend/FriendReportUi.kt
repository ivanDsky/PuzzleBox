package com.ivandsky.puzzlebox.play.friend

/** A line for the snackbar; [id] tells two equal lines apart so the second shows too. */
internal data class FriendNotice(val id: Int, val message: String)

/** The card's Report side: whether the sign-in prompt a signed-out Report opens is up, and the line to say, if any. */
internal data class FriendReportUi(val signInPrompt: Boolean = false, val notice: FriendNotice? = null)
