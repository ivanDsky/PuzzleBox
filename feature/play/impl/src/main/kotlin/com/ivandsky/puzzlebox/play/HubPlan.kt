package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId

/**
 * The hub's sections: the [hero] slot, the [alsoToday] tiles and the [doneToday] chips,
 * each list in registry order. A game sits in exactly one of them.
 */
internal data class HubPlan(
    val hero: HubHero,
    val alsoToday: List<GameId>,
    val doneToday: List<GameId>,
)

/**
 * The sections for [rows] with [next] up next. Unsolved rows other than [next] are Also
 * today, offline ones included (their tap retries); solved rows are Done today. With no
 * [next], the hero says why: a status not known yet is Loading, every row solved is
 * AllSolved, else only offline games are left (RestOffline).
 */
internal fun planHub(rows: List<HubRow>, next: GameId?): HubPlan {
    val (done, open) = rows.partition { it.status is TodayStatus.Solved }
    val hero = when {
        next != null -> HubHero.Next(next)
        rows.any { it.status == null } -> HubHero.Loading
        open.isEmpty() -> HubHero.AllSolved
        else -> HubHero.RestOffline
    }
    return HubPlan(
        hero = hero,
        alsoToday = open.map { it.game }.filter { it != next },
        doneToday = done.map { it.game },
    )
}
