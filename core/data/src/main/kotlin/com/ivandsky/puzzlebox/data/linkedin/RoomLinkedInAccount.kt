package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [LinkedInAccount] over the ghosts and fetch-log tables, scoped to the linked profile. The ghost reads see only the
 * boards [OwnerScope.visible] may see, like the day view does.
 */
internal class RoomLinkedInAccount(
    private val link: LinkedInLink,
    private val db: LinkedInDatabase,
    private val avatars: LinkedInAvatars,
    private val ownerScope: OwnerScope,
) : LinkedInAccount {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<LinkedInAccountData> =
        link.state.map { it.profileIdOrNull() }.distinctUntilChanged().flatMapLatest { profileId ->
            if (profileId == null) flowOf(EMPTY) else stored(profileId)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun stored(profileId: String): Flow<LinkedInAccountData> = ownerScope.visible.flatMapLatest { visibleOwner ->
        combine(
            db.ghostDao().observeNewestOf(profileId, visibleOwner),
            db.ghostDao().observeConnectionCount(profileId, visibleOwner),
            db.fetchLogDao().observeLastFetchedAt(profileId),
            avatars.present,
        ) { newest, connections, lastFetchedAtMs, photos ->
            LinkedInAccountData(
                owner = newest?.let { LinkedInOwner(it.name, avatars.pathOf(it.memberRef, photos)) },
                connectionsSeen = connections,
                lastFetchedAtMs = lastFetchedAtMs,
            )
        }
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
