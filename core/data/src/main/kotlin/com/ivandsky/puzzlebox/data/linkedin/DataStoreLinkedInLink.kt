package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.datastore.LinkedInLinkDataSource
import com.ivandsky.puzzlebox.datastore.LinkedInLinkPrefs
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The link over its own DataStore file, so the pause latch survives process death. It
 * only ever wipes the session store and never reads it: no cookie or token can reach a
 * log or a screen through here.
 */
internal class DataStoreLinkedInLink(
    private val source: LinkedInLinkDataSource,
    private val db: LinkedInDatabase,
    private val cookies: LinkedInSessionCookies,
    private val avatars: LinkedInAvatars,
) : LinkedInLink {

    override val state: Flow<LinkState> = source.link.map { it.toLinkState() }.distinctUntilChanged()

    override suspend fun connect(profileId: String, consentAtMs: Long) {
        source.connect(profileId, consentAtMs)
    }

    override suspend fun pause(kind: Kind) {
        source.pause(kind.name)
    }

    override suspend fun resume() {
        source.resume()
    }

    /**
     * The link state goes last on purpose: if the process dies halfway the link still reads
     * as linked and the player can press Disconnect again, rather than being left not
     * connected over a half-wiped store. Completions are not touched, so imported days stay. The photos go
     * with the rows they belong to: a disconnect leaves no connection's face on the phone.
     */
    override suspend fun disconnect() {
        cookies.clear()
        db.ghostDao().clear()
        db.fetchLogDao().clear()
        avatars.clear()
        source.clear()
    }
}

/**
 * A pause needs a profile to belong to, so a reason stored without one is ignored. A
 * reason this build does not know (written by a newer one) reads as [Kind.Unexpected]:
 * it must neither crash the reader nor silently re-open the latch.
 */
private fun LinkedInLinkPrefs.toLinkState(): LinkState {
    val profileId = profileId ?: return LinkState.NotConnected
    val stored = pauseReason ?: return LinkState.Connected(profileId)
    return LinkState.Paused(profileId, Kind.entries.firstOrNull { it.name == stored } ?: Kind.Unexpected)
}
