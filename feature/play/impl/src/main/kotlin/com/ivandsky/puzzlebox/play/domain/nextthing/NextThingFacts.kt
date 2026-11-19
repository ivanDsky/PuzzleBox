package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.nextthing.NextThing
import com.ivandsky.puzzlebox.domain.ranks.RankedBoard
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate

/**
 * What the providers of a [NextThingChain] decide from, for one solve. The lists are already on the phone.
 * The friend reads and the community list may reach the network, so they are asked for only by the provider that needs them,
 * inside the chain's time limit.
 *
 * @property game the game just solved.
 * @property playAgain the solve replays a day that is not today.
 * @property practice the puzzle has no edition number.
 * @property todaySet today's playable games in their order.
 * @property history every game's days, as the history lists them.
 * @property friendPuzzles the puzzles received from friends.
 * @property viewerUid the signed-in player; null when signed out.
 * @property friendBoard today's Friends board for [game], ranked among the viewer and their friends only;
 *   null when it cannot be read.
 * @property friendTier the visibility tier of the friend with that uid; null when the read fails.
 * @property communityPuzzles the community puzzles on offer, newest first, already without the player's own and
 *   the ones they hid; empty when the list is offline or refused. Like the friend reads it may be slow.
 */
internal data class NextThingFacts(
    val game: GameId,
    val today: LocalDate,
    val playAgain: Boolean,
    val practice: Boolean,
    val todaySet: List<TodayEntry>,
    val history: List<GameHistoryDay>,
    val friendPuzzles: List<FriendPuzzleRow>,
    val nextRollover: Instant,
    val viewerUid: String?,
    val friendBoard: suspend () -> RankedBoard?,
    val friendTier: suspend (uid: String) -> VisibilityTier?,
    val communityPuzzles: suspend () -> List<NextThing.CommunityPuzzle>,
)
