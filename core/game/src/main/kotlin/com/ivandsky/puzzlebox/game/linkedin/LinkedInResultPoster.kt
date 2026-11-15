package com.ivandsky.puzzlebox.game.linkedin

/** The post port: one method per request (ADR-0028 §3 kept). Never retries. */
interface LinkedInResultPoster {
    /** One GET of the game page. A page missing any required state is Rejected(Unexpected). */
    suspend fun gamePage(gamePath: String): LinkedInRead<LinkedInGamePage>

    /** One POST of the updateGameState server request for [post], carrying [page]'s server-issued values. */
    suspend fun send(post: LinkedInPost, page: LinkedInGamePage): LinkedInSendResult
}
