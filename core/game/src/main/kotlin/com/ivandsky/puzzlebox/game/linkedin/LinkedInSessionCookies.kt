package com.ivandsky.puzzlebox.game.linkedin

/** Read-the-session and wipe port over the WebView cookie store that holds the player's LinkedIn login. */
interface LinkedInSessionCookies {
    /** Null unless the store holds both `li_at` and `JSESSIONID` for linkedin.com. */
    suspend fun current(): LinkedInAccountSession?

    /** Wipes cookies and web storage. */
    suspend fun clear()
}
