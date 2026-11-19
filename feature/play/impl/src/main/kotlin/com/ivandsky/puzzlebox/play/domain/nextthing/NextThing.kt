package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.LocalDate

/** What Result's main button offers after a counted solve of today's puzzle (L3). It is offered, never started. */
sealed interface NextThing {

    /** Today's next unsolved [game]. */
    data class NextDaily(val game: GameId) : NextThing

    /** A duel with the friend [name], the player's nearest neighbour on today's Friends board (L4). */
    data class ChallengeFriend(val uid: String, val name: String) : NextThing

    /** The [game]'s puzzle of [day], on the phone and unsolved. */
    data class MissedDay(val game: GameId, val day: LocalDate) : NextThing

    /** A friend's puzzle the player received and has not played. */
    data class FriendPuzzle(val code: ShareCode, val game: GameId, val authorName: String) : NextThing

    /** A community puzzle whose author is not a friend. No provider offers it until the community one is registered. */
    data class CommunityPuzzle(val code: ShareCode, val game: GameId, val authorName: String) : NextThing

    /** Nothing is left to play today; the next set arrives at [nextRollover]. */
    data class DoneForToday(val nextRollover: Instant) : NextThing
}
