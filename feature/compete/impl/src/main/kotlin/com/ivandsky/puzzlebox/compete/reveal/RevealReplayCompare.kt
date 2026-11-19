package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey

/** The summary row's action that opens a leg's two replays side by side. */
internal const val CompareAction = "Compare"

/** A self duel's row says "Watch compare" with a play mark instead of the friend duel's bare [CompareAction]. */
internal const val WatchCompareLabel = "Watch compare"

/** What a screen reader says for leg [leg]'s [CompareAction]: the bare word would be the same on every row. */
internal fun compareDescription(leg: Int): String = "Compare puzzle ${leg + 1}"

internal const val MY_NAME = "You"

/**
 * The legs whose row offers [CompareAction]: the ones the view model has not ruled out (none before the reveal ran, none
 * without a replay on either side) whose game also draws a replay board. This is the only board check on the compare
 * path; the destination shows its empty page for the rest.
 */
internal fun offeredCompares(compareLegs: Set<Int>, gameOf: (Int) -> GameId?, brands: GamePlayRegistry): Set<Int> =
    compareLegs.filterTo(HashSet()) { leg -> gameOf(leg)?.let { brands[it]?.replayBoard } != null }

/** The compare of leg [open] of the duel [code], a puzzle of [game]: my side against the opponent's, ending on the reveal's own verdict. */
internal fun compareKey(code: String, game: GameId, open: LegCompare, self: Boolean = false): ReplayCompareKey =
    ReplayCompareKey.of(
        mine = ReplayKey.leg(game.value, code, open.leg),
        theirs = ReplayKey.leg(game.value, code, open.leg, theirs = true),
        myName = open.me,
        theirName = open.opponent,
        lead = when (open.finalLead) {
            Lead.Mine -> ReplayCompareKey.LEAD_MINE
            Lead.Theirs -> ReplayCompareKey.LEAD_THEIRS
            Lead.Level -> ReplayCompareKey.LEAD_LEVEL
        },
        theirsPast = self,
        theirsPastPhotoUrl = open.pastYouPhotoUrl.takeIf { self },
    )

/**
 * Hands a compare the view model has loaded over to its own destination, then drops it here: the destination reads both
 * replays again, so back from it lands on the summary with nothing held open.
 */
@Composable
internal fun OpenCompareEffect(
    compare: LegCompare?,
    code: String,
    gameOf: (Int) -> GameId?,
    navigator: Navigator,
    self: Boolean,
    onOpened: () -> Unit,
) {
    LaunchedEffect(compare) {
        val open = compare ?: return@LaunchedEffect
        val game = gameOf(open.leg)
        if (game != null) navigator.push(compareKey(code, game, open, self))
        onOpened()
    }
}
