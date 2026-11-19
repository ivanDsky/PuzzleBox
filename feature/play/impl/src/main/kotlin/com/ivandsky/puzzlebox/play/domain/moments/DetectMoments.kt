package com.ivandsky.puzzlebox.play.domain.moments

import com.ivandsky.puzzlebox.domain.moments.Moment

private val STREAK_MILESTONES = setOf(7, 30, 100)
private const val TOP_PLACES = 3
private const val TOP_MIN_PLAYERS = 4

/**
 * The moments one solve earned (L1), strongest first: personal best, first sub-X, top three among friends,
 * passed a friend, full set, streak. A play again or a practice puzzle earns none. Without
 * [SolveFacts.friends] the two friend moments are left out and the rest still come, so a caller can
 * ask at once and again when the board lands.
 */
internal fun detectMoments(facts: SolveFacts): List<Moment> {
    if (facts.playAgain || facts.practice) return emptyList()
    return listOfNotNull(
        Moment.PersonalBest.takeIf { facts.newBest },
        firstSubX(facts),
        facts.friends?.let(::topThree),
        facts.friends?.let(::passedFriend),
        Moment.FullSetDone(facts.dayTotal).takeIf { facts.totalToday > 0 && facts.doneToday == facts.totalToday },
        Moment.Streak(facts.streak).takeIf { facts.streak in STREAK_MILESTONES && facts.streakBefore < facts.streak },
    )
}

/** Hint-free solves only. Of the thresholds this time is under, only the tightest counts, and only if no earlier time was under it. */
private fun firstSubX(facts: SolveFacts): Moment.FirstSubX? {
    if (facts.hints > 0) return null
    val threshold = SubXThresholds.tightestUnder(facts.timeMs) ?: return null
    val metBefore = facts.bestHintFreeMs != null && facts.bestHintFreeMs < threshold
    return if (metBefore) null else Moment.FirstSubX(threshold)
}

private fun topThree(day: SolveFacts.FriendDay): Moment.TopThreeFriends? {
    val place = day.after.rows.firstOrNull { it.entry.uid == day.viewerUid }?.rank ?: return null
    return if (place <= TOP_PLACES && day.after.n >= TOP_MIN_PLAYERS) Moment.TopThreeFriends(place) else null
}

/**
 * A friend was passed when they were on the board before the solve, at or above the player's place then
 * (a player not yet on the board is below everyone on it), and the player is strictly above them now.
 * Of several, the one directly below the player now is named.
 */
private fun passedFriend(day: SolveFacts.FriendDay): Moment.PassedFriend? {
    val now = day.after.rows.firstOrNull { it.entry.uid == day.viewerUid }?.rank ?: return null
    val before = day.before.rows.associate { it.entry.uid to it.rank }
    val was = before[day.viewerUid]
    return day.after.rows
        .firstOrNull { row ->
            val friendWas = before[row.entry.uid]
            row.entry.uid != day.viewerUid && row.rank > now && friendWas != null && (was == null || friendWas <= was)
        }
        ?.let { Moment.PassedFriend(it.entry.uid, it.entry.displayName) }
}
