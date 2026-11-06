package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.model.GameId

/** What fills the hub's Up-next slot (spec A7): the next game, or why there is none. */
internal sealed interface HubHero {

    /** [game] is up next. */
    data class Next(val game: GameId) : HubHero

    /** Some game's status isn't known yet and none is next. */
    data object Loading : HubHero

    /** Every game is solved today. */
    data object AllSolved : HubHero

    /** Only offline games are left unsolved; they stay below as tiles whose tap retries. */
    data object RestOffline : HubHero
}
