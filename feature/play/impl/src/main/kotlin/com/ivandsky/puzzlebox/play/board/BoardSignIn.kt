package com.ivandsky.puzzlebox.play.friend

/** Where the board's sign-in stands; a cancelled sign-in is back to [Idle], a choice and not a failure. */
internal enum class BoardSignIn {
    Idle,
    SigningIn,

    /** Google or the account backend could not be reached. */
    Offline,

    /** Refused or unusable: worth trying again. */
    Failed,
}
