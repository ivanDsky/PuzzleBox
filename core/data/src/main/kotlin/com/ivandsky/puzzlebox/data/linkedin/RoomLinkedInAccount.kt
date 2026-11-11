package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** [LinkedInAccount] over the ghosts and fetch-log tables, scoped to the linked profile. */
internal class RoomLinkedInAccount(
    private val link: LinkedInLink,
    private val db: LinkedInDatabase,
    private val avatars: LinkedInAvatars,
) : LinkedInAccount {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<LinkedInAccountData> =
        link.state.map { it.profileIdOrNull() }.distinctUntilChanged().flatMapLatest { profileId ->
            if (profileId == null) flowOf(EMPTY) else stored(profileId)
        }

    private fun stored(profileId: String): Flow<LinkedInAccountData> = combine(
        db.ghostDao().observeNewestOf(profileId),
        db.ghostDao().observeConnectionCount(profileId),
        db.fetchLogDao().observeLastFetchedAt(profileId),
        avatars.present,
    ) { newest, connections, lastFetchedAtMs, photos ->
        LinkedInAccountData(
            owner = newest?.let { LinkedInOwner(it.name, avatars.pathOf(it.memberRef, photos)) },
            connectionsSeen = connections,
            lastFetchedAtMs = lastFetchedAtMs,
        )
    }

    private fun LinkState.profileIdOrNull(): String? = when (this) {
        LinkState.NotConnected -> null
        is LinkState.Connected -> profileId
        is LinkState.Paused -> profileId
    }

    private companion object {
        val EMPTY = LinkedInAccountData(owner = null, connectionsSeen = 0, lastFetchedAtMs = null)
    }
}
