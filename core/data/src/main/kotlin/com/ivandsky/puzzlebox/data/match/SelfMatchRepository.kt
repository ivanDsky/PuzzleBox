package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow

/**
 * The self-duel store: duels against your own first solves, kept on the device and never synced. It answers the
 * [MatchRepository] port for the codes it [holds], so every duel reader works on a self duel unchanged.
 *
 * A held duel reads back as a [Match] with the [SelfSeats] constants: challenger uid [SelfSeats.ME] (name and
 * photo as saved), opponent [SelfSeats.PAST] named [SelfSeats.PAST_NAME], no invite, [SelfSeats.NO_DEADLINE_HOURS],
 * never canceled or declined, no rematch source, `createdAt` to the millisecond, `progress[PAST]` = every leg
 * and `progress[ME]` = the legs posted so far.
 *
 * As a [MatchRepository]:
 * - [observe] is live: `Found` for a held code, else `NotFound`.
 * - [post] takes a [SelfSeats.ME] result for a held code: `Posted` the first time, `Duplicate` after (the first
 *   result stays), `Rejected` for an unknown code, another uid or a leg out of range. It is never `Late`.
 * - [readResult] is never sealed (sealing is the reader's job). [SelfSeats.PAST] gives the frozen result with
 *   `replay` = the puzzle's record replay, null when the first solve has none. [SelfSeats.ME] gives the posted
 *   result with `replay` null: my replay stays in the result outbox. Anything else is `Missing`.
 * - [observeMine] emits an empty list: self duels are listed through [observeAll].
 * - [create], [claim], [cancel], [decline] and [puzzleCodeTaken] are not offered here: they answer `Rejected`
 *   (`Unavailable(offline = false)` for the code check) and change nothing.
 */
interface SelfMatchRepository : MatchRepository {

    /** True when [code] is a self duel of this device. */
    suspend fun holds(code: ShareCode): Boolean

    /** Every self duel, newest first; re-emits on each change. */
    fun observeAll(): Flow<List<Match>>

    /**
     * Stores [match] with past you's results, one per leg ([past]`.legIndex`, time, hints and completion time
     * are kept). A code already held is replaced and its posted results are dropped.
     */
    suspend fun save(match: Match, past: List<LegResult>)

    /** Forgets the duel, past you's results and mine; a code not held is a no-op. */
    suspend fun remove(code: ShareCode)
}
