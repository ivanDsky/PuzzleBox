package com.ivandsky.puzzlebox.game.linkedin

/**
 * One method call sends exactly one GET, or none (SignedOut). Never retries, so a
 * rejection is reported once and the caller decides whether to pause.
 */
interface LinkedInAccountSource {
    /** Connect handshake: today's game for [gameTypeId]; returns the member's profile id from the game URN. */
    suspend fun check(gameTypeId: Int): LinkedInRead<String>

    suspend fun ownResult(profileId: String, gameTypeId: Int, puzzleNumber: Int): LinkedInRead<LinkedInOwnResult>

    /** Connections board for the day [delta] days before today; [todayNumber] builds today's URN. */
    suspend fun board(profileId: String, gameTypeId: Int, todayNumber: Int, delta: Int): LinkedInRead<LinkedInBoard>
}
