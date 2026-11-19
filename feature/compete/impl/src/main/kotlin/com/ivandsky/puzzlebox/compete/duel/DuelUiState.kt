package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.compete.create.DuelSignIn
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.SeatAction
import com.ivandsky.puzzlebox.domain.lineup.LegName
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/** What the invite card tells a viewer who holds no seat: the tail below the card follows the kind. */
internal enum class InviteKind { SignedOut, Open, Waiting, Taken, Cancelled }

/** Why the last seat call did not finish, and which [action] it was; the invite stays so the viewer can try again. */
internal sealed interface InviteNotice {
    val action: SeatAction

    data class Offline(override val action: SeatAction) : InviteNotice
    data class Closed(override val action: SeatAction) : InviteNotice
    data class Failed(override val action: SeatAction, val message: String?) : InviteNotice
}

/**
 * The card's facts about the duel: who [challenger] is, the [rounds] to
 * play and when it ends. [viewerIsChallenger] is a duel the viewer opened; [declined] a rematch the invitee refused;
 * [solvedBefore] when the viewer already has a completion of one of the puzzles; [canDecline] when the invite is
 * addressed to the viewer. [lineup] is every puzzle in leg order, which the card lists.
 */
internal data class InviteDetails(
    val challenger: Player,
    val rounds: Int,
    val deadline: Instant,
    val viewerIsChallenger: Boolean,
    val declined: Boolean,
    val solvedBefore: Boolean,
    val canDecline: Boolean,
    val lineup: List<InviteLeg>,
)

/** What the duel screen draws. */
internal sealed interface DuelUiState {
    data object Loading : DuelUiState

    /** No seat held. [details] is null only while signed out with the match unread. [working] is a seat call in flight. */
    data class Invite(
        val kind: InviteKind,
        val code: ShareCode,
        val details: InviteDetails?,
        val working: Boolean,
        val notice: InviteNotice?,
        val signIn: DuelSignIn,
    ) : DuelUiState

    /**
     * The viewer holds a seat; the puzzles screen takes [view] and the saved plays' [elapsedMs], by puzzle index.
     * A self duel also takes [firstSolvedAt] (the day past you set each score, by the puzzle's wire key), [myName]
     * (the signed-in name, null signed out), [removeAsked] (the remove-confirm showing) and [timeLeftShown] (the Play
     * setting: the leg timer counts down to past you's time). A custom puzzle is titled by its
     * [names] entry and the page explains a puzzle taken down or offline. [pastYouPhotoUrl] is the player's own photo
     * (null with CompeteFlag.PastYouPhoto off, signed out or no photo): a self duel's Past you wears it.
     */
    data class Participant(
        val view: MatchView,
        val elapsedMs: Map<Int, Long>,
        val firstSolvedAt: Map<String, Instant> = emptyMap(),
        val myName: String? = null,
        val removeAsked: Boolean = false,
        val timeLeftShown: Boolean = true,
        val names: Map<ShareCode, LegName> = emptyMap(),
        val pastYouPhotoUrl: String? = null,
    ) : DuelUiState

    data object NotFound : DuelUiState
    data object Offline : DuelUiState
    data object UpdateApp : DuelUiState
}
