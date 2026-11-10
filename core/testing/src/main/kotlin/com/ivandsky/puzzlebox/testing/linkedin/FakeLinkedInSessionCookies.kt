package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies

/** In-memory session store. A clear leaves nothing to read, as a real wipe does. */
class FakeLinkedInSessionCookies(private val session: LinkedInAccountSession?) : LinkedInSessionCookies {
    var cleared: Boolean = false
        private set

    override suspend fun current(): LinkedInAccountSession? = if (cleared) null else session

    override suspend fun clear() {
        cleared = true
    }
}
