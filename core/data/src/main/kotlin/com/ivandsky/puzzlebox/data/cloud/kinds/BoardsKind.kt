package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInItem
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOutcome
import com.ivandsky.puzzlebox.data.linkedin.LinkedInStoreLock
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Saved LinkedIn boards as the synced kind `boards` (ADR-0029 decisions 1 and 8): one doc per
 * `{game}_{day}_{profileKey}`, `{formatVersion, game, day, profileKey, readAtMs, complete, rows}` with every key from
 * [BoardDocKeys]. A row carries name, time and flags only: no member ref, no photo, so a third party's profile id never
 * leaves the phone, and no session value exists in these tables to leak.
 *
 * A board is pending while its `uploadedFingerprint` is null: the ghost writer clears it on every rewrite. An
 * acknowledgement sets the fingerprint of the uploaded content only while the stored board still has that content, so
 * a board re-read during an upload stays pending. Boards carry no tombstone (A27).
 *
 * [apply] takes [LinkedInStoreLock] once, from outside, and never nests it. A restore keeps one board per
 * (game, day) by [BoardMerge], and marks the day read in the fetch log of the linked profile when the restored board
 * is that profile's, so no LinkedIn call is spent re-reading it (A26). The cloud holds no member ids, so a restored
 * connection gets a stable stand-in ref made from its name, and the board's own profile a stand-in id made from its
 * key, until the day is read again.
 */
internal class BoardsKind(
    private val db: LinkedInDatabase,
    private val storeLock: LinkedInStoreLock,
    private val link: LinkedInLink,
) : SyncedKind {

    private val boards = db.savedBoardDao()
    private val ghosts = db.ghostDao()
    private val fetchLog = db.fetchLogDao()

    override val kind: String = "boards"

    override fun pendingIds(uid: String): Flow<Set<String>> =
        db.invalidationTracker.createFlow("saved_boards")
            .map { boards.pending(uid).mapTo(HashSet(), ::docIdOf) }
            .distinctUntilChanged()

    override suspend fun adopt(uid: String) = boards.adoptNullOwner(uid)

    override suspend fun pending(uid: String): List<CloudDoc> = db.withTransaction {
        boards.pending(uid).map { board -> encode(valueOf(board)) }
    }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int {
        val incoming = docs.mapNotNull(::decode).groupBy { it.game to it.day }
        if (incoming.isEmpty()) return 0
        val linkedId = linkedProfileId()
        return storeLock.withLock {
            db.withTransaction {
                var restored = 0
                for ((place, offered) in incoming) {
                    val local = boards.get(place.first, place.second)
                    // Another account's board is never touched, and no board of this account replaces it.
                    if (local != null && local.ownerUid != null && local.ownerUid != uid) continue
                    val localValue = local?.let { valueOf(it) }
                    val linkedKey = linkedId?.let(::profileKeyOf)
                    val winner = BoardMerge.winner(listOfNotNull(localValue) + offered, linkedKey)
                    if (winner === localValue) continue
                    restore(uid, winner, local, linkedId)
                    restored++
                }
                restored
            }
        }
    }

    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) = db.withTransaction {
        for (sent in uploaded.mapNotNull(::decode)) {
            val board = boards.get(sent.game, sent.day)?.takeIf { it.ownerUid == uid } ?: continue
            val current = valueOf(board)
            if (current.fingerprint() == sent.fingerprint()) boards.setFingerprint(sent.game, sent.day, current.fingerprint())
        }
    }

    override suspend fun forget(uid: String) = boards.clearFingerprints(uid)

    private suspend fun linkedProfileId(): String? = when (val state = link.state.first()) {
        LinkState.NotConnected -> null
        is LinkState.Connected -> state.profileId
        is LinkState.Paused -> state.profileId
    }

    private suspend fun valueOf(board: SavedBoardEntity): BoardValue = BoardValue(
        game = board.game,
        day = board.day,
        profileKey = profileKeyOf(board.profileId),
        readAtMs = board.readAtMs,
        complete = board.complete,
        rows = ghosts.board(board.game, board.day).map {
            BoardRow(it.name, it.timeMs, it.hintFree, it.flawless, isOwn = it.memberRef == board.profileId)
        },
    )

    /** Writes [won] over the local board of its day: the cloud's content, owned by [uid], already uploaded. */
    private suspend fun restore(uid: String, won: BoardValue, local: SavedBoardEntity?, linkedId: String?) {
        val profileId = when {
            linkedId != null && profileKeyOf(linkedId) == won.profileKey -> linkedId
            local != null && profileKeyOf(local.profileId) == won.profileKey -> local.profileId
            else -> RESTORED_PROFILE + won.profileKey
        }
        ghosts.replace(won.game, won.day, ghostsOf(won, profileId))
        boards.upsert(
            SavedBoardEntity(won.game, won.day, profileId, uid, won.readAtMs, won.complete, won.fingerprint()),
        )
        if (linkedId != null && profileKeyOf(linkedId) == won.profileKey) markRead(won, linkedId)
    }

    private suspend fun markRead(board: BoardValue, profileId: String) {
        val item = LinkedInItem.Board.name
        val last = fetchLog.get(board.game, board.day, item, profileId)
        if (last != null && last.fetchedAtMs >= board.readAtMs) return
        val outcome = if (board.complete) LinkedInOutcome.Board else LinkedInOutcome.BoardToday
        fetchLog.put(FetchLogEntity(board.game, board.day, item, profileId, board.readAtMs, outcome.name))
    }

    private fun ghostsOf(board: BoardValue, profileId: String): List<GhostEntity> {
        val seen = HashMap<String, Int>()
        return board.rows.map { row ->
            val ref = if (row.isOwn) profileId else standIn(row.name, seen)
            GhostEntity(board.game, board.day, ref, row.name, row.timeMs, row.hintFree, row.flawless, board.readAtMs)
        }
    }

    /** A ref for a connection whose id the cloud never held: stable per name, apart for two with the same name. */
    private fun standIn(name: String, seen: MutableMap<String, Int>): String {
        val base = STAND_IN + sha256Hex(name).take(KEY_LENGTH)
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        return if (n == 1) base else "$base#$n"
    }

    private fun encode(board: BoardValue): CloudDoc {
        val fields = mapOf(
            BoardDocKeys.FORMAT_VERSION to FORMAT_VERSION,
            BoardDocKeys.GAME to board.game,
            BoardDocKeys.DAY to board.day,
            BoardDocKeys.PROFILE_KEY to board.profileKey,
            BoardDocKeys.READ_AT_MS to board.readAtMs,
            BoardDocKeys.COMPLETE to board.complete,
            BoardDocKeys.ROWS to board.rows.sortedWith(ROW_ORDER).map(::encode),
        )
        return CloudDoc(docIdOf(board.game, board.day, board.profileKey), fields.filterKeys { it in BoardDocKeys.DOC })
    }

    private fun encode(row: BoardRow): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>(
            BoardDocKeys.NAME to row.name,
            BoardDocKeys.HINT_FREE to row.hintFree,
            BoardDocKeys.FLAWLESS to row.flawless,
            BoardDocKeys.IS_OWN to row.isOwn,
        )
        row.timeMs?.let { fields[BoardDocKeys.TIME_MS] = it }
        return fields.filterKeys { it in BoardDocKeys.ROW }
    }

    /** Null for a doc that cannot be used: a missing or mistyped key, a day that is no date, or an id that is not its own. */
    private fun decode(doc: CloudDoc): BoardValue? {
        val f = doc.fields
        val game = (f[BoardDocKeys.GAME] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val day = f[BoardDocKeys.DAY] as? String ?: return null
        val key = (f[BoardDocKeys.PROFILE_KEY] as? String)?.takeIf { it.isNotBlank() } ?: return null
        if (!isDate(day) || doc.id != docIdOf(game, day, key)) return null
        val rawRows = f[BoardDocKeys.ROWS] as? List<*> ?: return null
        var ownSeen = false
        val rows = rawRows.map { raw ->
            val row = raw as? Map<*, *> ?: return null
            val own = row[BoardDocKeys.IS_OWN] == true && !ownSeen
            ownSeen = ownSeen || own
            BoardRow(
                name = row[BoardDocKeys.NAME] as? String ?: return null,
                timeMs = (row[BoardDocKeys.TIME_MS] as? Number)?.toLong(),
                hintFree = row[BoardDocKeys.HINT_FREE] == true,
                flawless = row[BoardDocKeys.FLAWLESS] == true,
                isOwn = own,
            )
        }
        return BoardValue(
            game = game,
            day = day,
            profileKey = key,
            readAtMs = (f[BoardDocKeys.READ_AT_MS] as? Number)?.toLong() ?: return null,
            complete = f[BoardDocKeys.COMPLETE] as? Boolean ?: return null,
            rows = rows,
        )
    }

    private fun isDate(text: String): Boolean = try {
        LocalDate.parse(text)
        true
    } catch (_: DateTimeParseException) {
        false
    }

    private companion object {
        const val FORMAT_VERSION = 1L
    }
}

/** The stand-in a restored board gets for the profile id the cloud does not hold; it carries the profile key. */
private const val RESTORED_PROFILE = "key:"

private const val STAND_IN = "name:"

private const val KEY_LENGTH = 16

/** The first 16 hex characters of the SHA-256 of a LinkedIn profile id: what the cloud knows of a profile. */
internal fun profileKeyOf(profileId: String): String =
    if (profileId.startsWith(RESTORED_PROFILE)) profileId.removePrefix(RESTORED_PROFILE) else sha256Hex(profileId).take(KEY_LENGTH)

/** The id of a board's doc in the cloud space. */
internal fun docIdOf(board: SavedBoardEntity): String = docIdOf(board.game, board.day, profileKeyOf(board.profileId))

private fun docIdOf(game: String, day: String, profileKey: String): String = "${game}_${day}_$profileKey"
