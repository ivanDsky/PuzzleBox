package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.duel.customCodes
import com.ivandsky.puzzlebox.compete.duel.inviteLegs
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.last

/**
 * A friend duel that was created: its [code], the game name of each puzzle in order ([puzzles]), and the [deadlineHours]
 * it was created with, which the invite text states. Carried whole because the sheet that shows it may have been reset since.
 * [legs] is every puzzle's reference in order when the invite lists the lineup (DuelLineup on), else empty.
 */
internal data class CreatedDuel(val code: ShareCode, val puzzles: List<String>, val deadlineHours: Int, val legs: List<LegRef> = emptyList())

/**
 * What the invite text lists after "best of n": the games once each, or with the lineup on every puzzle in order, a
 * custom one by its title in quotes ([legTitles] reads it; without it, or when it cannot, "<Game> · <code>").
 */
internal suspend fun CreatedDuel.inviteGames(legTitles: ObserveLegTitles?): List<String> {
    if (legs.isEmpty()) return puzzles.distinct()
    val names = legs.map { it.gameId }.zip(puzzles).toMap()
    val titles = legTitles?.invoke(legs.customCodes())?.last().orEmpty()
    return inviteLegs(legs, names::getValue, titles).map { it.label }
}
