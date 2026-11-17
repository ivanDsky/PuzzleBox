package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.datastore.LinkedInLinkDataSource
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What this phone remembers of its own posts: the days it posted, and the days it began to send with no
 * known outcome yet. Kept in the link file, so it belongs to the linked profile, is wiped with the link on
 * disconnect and is never synced or backed up.
 */
internal class LinkedInPostLog(private val source: LinkedInLinkDataSource) {

    /** What is remembered of one game day. */
    data class Entry(val posted: Boolean, val sending: Boolean, val already: Boolean)

    fun observe(game: GameId, day: LocalDate): Flow<Entry> {
        val ref = ref(game, day)
        return source.link.map { Entry(posted = ref in it.postedRefs, sending = ref in it.sendingRefs, already = ref in it.alreadyRefs) }.distinctUntilChanged()
    }

    /** Persisted before the send, so a send whose answer never comes is still known about. False when [key]'s profile is no longer linked: nothing is stored and nothing may be sent. */
    suspend fun markSending(key: DayKey): Boolean = source.markSending(key.profileId, ref(key.game, key.day))

    /**
     * The send has a known outcome: the marker goes, [posted] remembers the day as posted and [already] as held by
     * LinkedIn without this phone's post. Dropped when [key]'s profile is no longer linked.
     */
    suspend fun settle(key: DayKey, posted: Boolean, already: Boolean = false) {
        source.settleSend(key.profileId, ref(key.game, key.day), posted, already)
    }

    private fun ref(game: GameId, day: LocalDate): String = "${game.value}/$day"
}
