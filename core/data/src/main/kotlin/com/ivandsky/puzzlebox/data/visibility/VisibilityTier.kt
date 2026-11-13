package com.ivandsky.puzzlebox.data.visibility

/** How much of a player the leaderboard hub exposes; declaration order is the ladder, each tier includes the ones below. */
enum class VisibilityTier(val storageName: String) {
    Hidden("hidden"),
    Board("board"),
    Duel("duel"),
    Replays("replays"),
    ;

    val postsToBoards: Boolean get() = this != Hidden
    val allowsChallenge: Boolean get() = this >= Duel
    val allowsReplays: Boolean get() = this == Replays

    companion object {
        /** The tier stored as [name], or null when it names none (a newer client's tier, a corrupt doc). */
        fun fromStorageName(name: String?): VisibilityTier? = entries.firstOrNull { it.storageName == name }
    }
}
