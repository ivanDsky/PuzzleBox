package com.ivandsky.puzzlebox.data.cloud

/** Why sync is paused. */
enum class PauseReason {
    /** The project's daily quota is spent. */
    Exhausted,

    /** The backend's rules refused the account. */
    Denied,

    /** This device hit its own daily write cap. */
    WriteCap,
}
