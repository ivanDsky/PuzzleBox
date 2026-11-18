package com.ivandsky.puzzlebox.play.board

/** What a screen tells the player about this sign-in state: why it did not finish, null while there is nothing to say. */
internal fun BoardSignIn.note(): String? = when (this) {
    BoardSignIn.Failed -> "Couldn't sign in. Try again."
    BoardSignIn.Offline -> "You're offline. Sign in once you're back."
    BoardSignIn.Idle, BoardSignIn.SigningIn -> null
}
