package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.database.SelfMatchDao
import com.ivandsky.puzzlebox.database.SelfMatchEntity
import com.ivandsky.puzzlebox.database.SelfPastLegEntity
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room adapter of [SelfMatchRepository] over `self_matches` and `self_past_legs` ([dao]). Past you's results
 * are the `self_past_legs` rows; the challenger, the legs and my posted results live in the row's `legs` text
 * (see [encode]). The settings columns are derived from the match: `roundCount` = its leg count, `games` = its
 * distinct game ids joined by commas. A row this version cannot read counts as held but reads as not found and
 * stays out of the list. Past you's replay is read from [completions] through [registry] on every read.
 */
internal class RoomSelfMatchRepository(
    private val dao: SelfMatchDao,
    private val completions: CompletionRepository,
    private val registry: GameRegistry,
) : SelfMatchRepository {

    /** A post reads the row and writes it back; one writer at a time keeps a save or another post from being undone. */
    private val writes = Mutex()

    override suspend fun holds(code: ShareCode): Boolean = dao.match(code.value) != null

    override fun observeAll(): Flow<List<Match>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toMatch() } }.distinctUntilChanged()

    override suspend fun save(match: Match, past: List<LegResult>) = writes.withLock {
        dao.insert(
            SelfMatchEntity(
                code = match.code.value,
                createdAtMs = match.createdAt.toEpochMilli(),
                roundCount = match.legs.size,
                games = match.legs.map { it.ref.gameId.value }.distinct().joinToString(GAMES_SEPARATOR),
                includeFriends = match.includeFriends,
                legs = encode(StoredDuel(match.challenger, match.legs.map { StoredLeg(it, mine = null) })),
            ),
            past.map { SelfPastLegEntity(match.code.value, it.legIndex, it.timeMs, it.hints, it.completedAtMs) },
        )
    }

    override suspend fun remove(code: ShareCode) = writes.withLock { dao.delete(code.value) }

    override fun observe(code: ShareCode): Flow<MatchFetch> =
        dao.observeMatch(code.value)
            .map { row -> row?.toMatch()?.let(MatchFetch::Found) ?: MatchFetch.NotFound }
            .distinctUntilChanged()

    override fun observeMine(): Flow<List<Match>?> = flowOf(emptyList())

    override suspend fun post(code: ShareCode, result: LegResult): PostResult = writes.withLock {
        val row = dao.match(code.value) ?: return PostResult.Rejected("no self duel ${code.value}")
        val duel = decode(row.legs) ?: return PostResult.Rejected("unreadable self duel ${code.value}")
        val leg = duel.legs.getOrNull(result.legIndex) ?: return PostResult.Rejected("no leg ${result.legIndex}")
        when {
            result.uid != SelfSeats.ME -> PostResult.Rejected("only my seat posts to a self duel")
            leg.mine != null -> PostResult.Duplicate
            else -> {
                // My replay stays in the result outbox: up to 12 KiB a leg would ride every list emission.
                val posted = StoredLeg(leg.leg, result.copy(replay = null))
                val legs = duel.legs.mapIndexed { index, old -> if (index == result.legIndex) posted else old }
                dao.insertMatch(row.copy(legs = encode(StoredDuel(duel.challenger, legs))))
                PostResult.Posted
            }
        }
    }

    override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
        val duel = dao.match(code.value)?.let { decode(it.legs) } ?: return ResultRead.Missing
        val leg = duel.legs.getOrNull(legIndex) ?: return ResultRead.Missing
        val result = when (uid) {
            SelfSeats.ME -> leg.mine
            SelfSeats.PAST -> dao.pastLegs(code.value).firstOrNull { it.legIndex == legIndex }?.let { past ->
                LegResult(SelfSeats.PAST, legIndex, past.timeMs, past.hints, past.completedAtMs, gaveUp = false, recordReplay(leg.leg.ref))
            }
            else -> null
        }
        return result?.let(ResultRead::Found) ?: ResultRead.Missing
    }

    private suspend fun recordReplay(ref: LegRef) =
        PuzzleRefKeys.decode(ref.wire, registry)?.let { completions.recordReplay(it) }

    override suspend fun puzzleCodeTaken(code: ShareCode): PuzzleCodeCheck = PuzzleCodeCheck.Unavailable(offline = false, NOT_OFFERED)
    override suspend fun create(draft: MatchDraft): CreateResult = CreateResult.Rejected(NOT_OFFERED)
    override suspend fun claim(code: ShareCode, me: Player): ClaimResult = ClaimResult.Rejected(NOT_OFFERED)
    override suspend fun cancel(code: ShareCode): WriteResult = WriteResult.Rejected(NOT_OFFERED)
    override suspend fun decline(code: ShareCode): WriteResult = WriteResult.Rejected(NOT_OFFERED)
}

private const val NOT_OFFERED = "a self duel is saved and removed on the device, never shared"
private const val GAMES_SEPARATOR = ","

private const val FORMAT = "1"
private const val LINE = '\n'
private const val FIELD = '|'

/** An absent value. URL encoding never emits it, so it cannot collide with a real name or uid. */
private const val NONE = "~"

private class StoredDuel(val challenger: Player, val legs: List<StoredLeg>)

private class StoredLeg(val leg: MatchLeg, val mine: LegResult?)

private fun SelfMatchEntity.toMatch(): Match? {
    val code = ShareCode.parse(code) ?: return null
    val duel = decode(legs) ?: return null
    return Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = Instant.ofEpochMilli(createdAtMs),
        deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
        challenger = duel.challenger,
        invitedUid = null,
        opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
        legs = duel.legs.map { it.leg },
        includeFriends = includeFriends,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = mapOf(
            SelfSeats.PAST to duel.legs.indices.toSet(),
            SelfSeats.ME to duel.legs.indices.filter { duel.legs[it].mine != null }.toSet(),
        ),
    )
}

/**
 * The `legs` text: a head line `1|name|photo` for the challenger, then one `author|mine|ref` line per leg in
 * order. `ref` is [LegRef.wire] and comes last so its own characters never split a line; `mine` is
 * `timeMs,hints,completedAtMs,gaveUp` once posted; names, urls and uids are URL-encoded; [NONE] marks an absent value.
 */
private fun encode(duel: StoredDuel): String = buildString {
    append(FORMAT).append(FIELD).append(text(duel.challenger.name)).append(FIELD).append(text(duel.challenger.photoUrl))
    duel.legs.forEach { stored ->
        val mine = stored.mine?.run { "$timeMs,$hints,$completedAtMs,$gaveUp" } ?: NONE
        append(LINE).append(text(stored.leg.authorUid)).append(FIELD).append(mine).append(FIELD).append(stored.leg.ref.wire)
    }
}

/** Null for text this version cannot read: every strict conversion here fails with an IllegalArgumentException. */
private fun decode(text: String): StoredDuel? = try {
    val lines = text.split(LINE)
    val head = lines.first().split(FIELD)
    require(head.size == 3 && head[0] == FORMAT)
    val challenger = Player(SelfSeats.ME, requireNotNull(untext(head[1])), untext(head[2]))
    StoredDuel(challenger, lines.drop(1).mapIndexed(::storedLeg))
} catch (_: IllegalArgumentException) {
    null
}

private fun storedLeg(index: Int, line: String): StoredLeg {
    val fields = line.split(FIELD, limit = 3)
    require(fields.size == 3)
    val mine = fields[1].takeIf { it != NONE }?.split(',')?.let { parts ->
        require(parts.size == 4)
        LegResult(SelfSeats.ME, index, parts[0].toLong(), parts[1].toInt(), parts[2].toLong(), parts[3].toBooleanStrict())
    }
    return StoredLeg(MatchLeg(requireNotNull(LegRef.parse(fields[2])), untext(fields[0])), mine)
}

private fun text(value: String?): String = value?.let { URLEncoder.encode(it, ENCODING) } ?: NONE

private fun untext(field: String): String? = field.takeIf { it != NONE }?.let { URLDecoder.decode(it, ENCODING) }

private const val ENCODING = "UTF-8"
