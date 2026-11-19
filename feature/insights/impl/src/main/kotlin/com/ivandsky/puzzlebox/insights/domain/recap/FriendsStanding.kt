package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * What the friends read of one recap period came to: the input of the Best game among friends card (A12, A16, A21).
 * The recap's local cards never wait for it, so every [RecapInput] starts as [Reading] and carries the answer once
 * [ResolveFriendsStanding] has it.
 */
internal sealed interface FriendsStanding {

    /** Not answered yet: the story is told without a friends card, and the card joins it when the answer lands. */
    data object Reading : FriendsStanding

    /** Signed out, no friends, nothing played among them, or a year (until the yearly recap fills that branch): the story has no friends card (A12). */
    data object NotApplicable : FriendsStanding

    /** An answer the story shows as a card. */
    sealed interface Shown : FriendsStanding

    /** [game] earned the viewer the most Friends Overall points over the period: [points] of them. */
    data class Loaded(val game: GameId, val points: Double) : Shown

    /** The month just ended is still taking late results among friends; they settle on [settlesOn] (A21), so no number is said yet. */
    data class Settling(val settlesOn: LocalDate) : Shown

    /** The read failed, took too long or only half answered, as when offline: the card says so in its own place (A16). */
    data object Unavailable : Shown
}
