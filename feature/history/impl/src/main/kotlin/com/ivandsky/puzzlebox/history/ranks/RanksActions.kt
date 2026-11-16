package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * What the hub's taps, swipes and sheets do, the same on the phone and the tablet; the route turns each into a view
 * model call or a push. A null one means the hub has no such control: no back arrow, no row taps, no pull to refresh.
 * [onPlayThisDay], [tuneLabelOpensSheet] and [heroJumpsToRow] are the nav rethink's doors: Play this day on a game's unplayed day,
 * the Tune label as a button, the hero card jumping to the viewer's row; each is off (null, false) unless the route turns it on.
 */
internal data class RanksActions(
    val onSignIn: () -> Unit,
    val onSwipe: (Int) -> Unit,
    val onBack: (() -> Unit)? = null,
    val onReset: () -> Unit = {},
    val onPickBoard: (RanksBoard) -> Unit = {},
    val onNudgeShown: () -> Unit = {},
    val onScope: (RankScope) -> Unit = {},
    val onWindow: (RanksSelection.Window) -> Unit = {},
    val onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit = { _, _ -> },
    val onSelectDay: (LocalDate?) -> Unit = {},
    val onPlayAgain: (GameId, LocalDate) -> Unit = { _, _ -> },
    val onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit = { _, _ -> },
    val onCompare: (HistoryDayRow) -> Unit = {},
    val onWatchPlayer: (PlayerSheetViewModel.Target) -> Unit = {},
    val onOpenPlayer: ((PlayerSheetViewModel.Target) -> Unit)? = null,
    val onChallenge: (GameId) -> Unit = {},
    val onClosePlayer: () -> Unit = {},
    val onShareReplays: () -> Unit = {},
    val onKeepReplaysPrivate: () -> Unit = {},
    val onRefresh: (() -> Unit)? = null,
    val onPlayThisDay: ((GameId, LocalDate) -> Unit)? = null,
    val tuneLabelOpensSheet: Boolean = false,
    val heroJumpsToRow: Boolean = false,
)
