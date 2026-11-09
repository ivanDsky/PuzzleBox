package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import java.time.Instant

/**
 * The one reveal-ready rule (28's RevealReady row, 36's "See the reveal" footer). False for a cancelled, declined or
 * unclaimed match. Otherwise true when [now] >= closesAt, or when [mine] has a row of [me] for every leg index, each
 * Posted, Late or LocalOnly (Late and LocalOnly are final; a Pending row is not done), and the opponent's
 * `progress` covers every leg index.
 */
fun Match.revealReady(me: String, mine: List<OutboxResult>, now: Instant): Boolean {
    if (cancelled || declined) return false
    val them = opponentOf(me) ?: return false
    if (now >= closesAt) return true
    val done = mine.filter { it.result.uid == me && it.state != ResultPostState.Pending }
        .mapTo(HashSet()) { it.result.legIndex }
    val theirs = progress[them.uid].orEmpty()
    return legs.indices.all { it in done && it in theirs }
}
