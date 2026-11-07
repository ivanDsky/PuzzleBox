package com.ivandsky.puzzlebox.data.model

/** Where this user's result for a received puzzle stands on the puzzle's board; stored by name. */
enum class PostState {
    /** Not posted yet; a post is still to be tried. */
    Pending,

    /** The board holds this user's result. */
    Posted,

    /** The result stays on this device only. */
    LocalOnly,
}
