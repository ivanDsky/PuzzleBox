package com.ivandsky.puzzlebox.game.linkedin

/** The post port: one method per request (ADR-0028 §3 kept). Never retries. */
interface LinkedInResultPoster {
    /**
     * One GET of the game page [post] starts with: the game's state on LinkedIn, over the wire [post]'s play saves
     * through. A page missing any required state is Rejected(Unexpected).
     */
    suspend fun gamePage(post: LinkedInPost): LinkedInRead<LinkedInGamePage>

    /** The save of [post] over its play's wire, carrying [page]'s server-issued values: the opening when the page holds no play, then the win. */
    suspend fun send(post: LinkedInPost, page: LinkedInGamePage): LinkedInSendResult
}
