package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.duel.customCodes
import com.ivandsky.puzzlebox.compete.duel.inviteLegs
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A friend duel that was created: its [code], the game name of each puzzle in order ([puzzles]), and the [deadlineHours]
 * it was created with, which the invite text states. Carried whole because the sheet that shows it may have been reset since.
 * [legs] is every puzzle's reference in order, which the invite lists.
 */
internal data class CreatedDuel(val code: ShareCode, val puzzles: List<String>, val deadlineHours: Int, val legs: List<LegRef>)

/** How long the share sheet waits for public titles; a puzzle whose title is not in by then shows its code. */
internal const val TITLES_WAIT_MS = 2_000L

/**
 * What the invite text lists after "best of n": every puzzle in order, a custom one by its title in quotes
 * ([legTitles] reads them, for at most [TITLES_WAIT_MS]; a title not known by then or unreadable shows "<Game> · <code>").
 */
internal suspend fun CreatedDuel.inviteGames(legTitles: ObserveLegTitles): List<String> {
    val names = legs.map { it.gameId }.zip(puzzles).toMap()
    var titles = emptyMap<ShareCode, String>()
    withTimeoutOrNull(TITLES_WAIT_MS) { legTitles(legs.customCodes()).collect { titles = it } }
    return inviteLegs(legs, names::getValue, titles).map { it.label }
}
