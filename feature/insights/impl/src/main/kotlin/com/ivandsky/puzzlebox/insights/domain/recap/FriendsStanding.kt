package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * What the friends read of one recap period came to: the input of the Best game among friends card (A12, A16, A21).
 * The recap's local cards never wait for it. When the card applies at all, which the story knows without the network,
 * it holds its slot as [Reading] from the first emission and [ResolveFriendsStanding] settles it in place; only
 * [NotApplicable] leaves the story without the card.
 */
internal sealed interface FriendsStanding {

    /** Signed out, nothing played, no friends found, or a year with fewer than [ResolveFriendsStanding.MIN_KEPT_WEEKS] kept weeks: the story has no friends card (A12). */
    data object NotApplicable : FriendsStanding

    /** A state the story shows as a card. */
    sealed interface Shown : FriendsStanding

    /** The friends read is still out: the card holds its place and says so quietly. */
    data object Reading : Shown

    /**
     * [game] earned the viewer the most Friends Overall points over the period: [points] of them. A year has no month
     * window to read, so its [points] are the average a day over its [weeks] kept weeks and say "based on [weeks]
     * weeks" (A12, A19); a month's [weeks] is null and its points are the window's total.
     */
    data class Loaded(val game: GameId, val points: Double, val weeks: Int? = null) : Shown

    /** The month just ended is still taking late results among friends; they settle on [settlesOn] (A21), so no number is said yet. */
    data class Settling(val settlesOn: LocalDate) : Shown

    /** The read failed, took too long or only half answered, as when offline: the card says so in its own place (A16). */
    data object Unavailable : Shown
}
