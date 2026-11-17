package com.ivandsky.puzzlebox.game.linkedin

/** The post port: one method per request (ADR-0028 §3 kept). Never retries. */
interface LinkedInResultPoster {
    /** One GET of the game page. A page missing any required state is Rejected(Unexpected). */
    suspend fun gamePage(gamePath: String): LinkedInRead<LinkedInGamePage>

    /**
     * The read a post starts with: the game's state on LinkedIn, over the wire [post]'s play saves through. The
     * route alone names the `updateGameState` page, so that is the default; the voyager wire reads by game type.
     */
    suspend fun gamePage(post: LinkedInPost): LinkedInRead<LinkedInGamePage> = gamePage(post.gamePath)

    /** The save of [post] over its play's wire, carrying [page]'s server-issued values: the opening when the page holds no play, then the win. */
    suspend fun send(post: LinkedInPost, page: LinkedInGamePage): LinkedInSendResult
}
