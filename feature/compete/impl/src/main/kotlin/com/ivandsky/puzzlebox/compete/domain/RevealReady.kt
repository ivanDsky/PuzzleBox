package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import java.time.Instant

/**
 * The one reveal-ready rule (the Duels tab's "See results" row and the duel screen's "See results" footer). False
 * for a cancelled, declined or unclaimed match. Otherwise true when [now] >= closesAt, or when I have played every
 * leg index and the opponent's `progress` covers every leg index. A leg is played by me when [mine] has a row of
 * [me] for it that is Posted, Late or LocalOnly (Late and LocalOnly are final; a Pending row is not done); a leg
 * [mine] has no row for is played when my own entry in `progress` names it, which is how a puzzle played on another
 * device counts here.
 */
fun Match.revealReady(me: String, mine: List<OutboxResult>, now: Instant): Boolean {
    if (cancelled || declined) return false
    val them = opponentOf(me) ?: return false
    if (now >= closesAt) return true
    val rows = mine.filter { it.result.uid == me }
    val queued = rows.mapTo(HashSet()) { it.result.legIndex }
    val done = rows.filter { it.state != ResultPostState.Pending }.mapTo(HashSet()) { it.result.legIndex } +
        progress[me].orEmpty().filter { it !in queued }
    val theirs = progress[them.uid].orEmpty()
    return legs.indices.all { it in done && it in theirs }
}
