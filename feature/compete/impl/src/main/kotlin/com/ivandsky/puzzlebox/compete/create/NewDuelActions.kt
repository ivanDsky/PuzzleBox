package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor

/** What New duel's panels can do; the host binds each to the view model. */
internal class NewDuelActions(
    val onQuick: () -> Unit,
    val onDaily: () -> Unit,
    val onToggle: (DuelGame) -> Unit,
    val onDeadline: (Int) -> Unit,
    val onFriends: (Boolean) -> Unit,
    val onOpponent: (Opponent) -> Unit,
    val onCreate: () -> Unit,
    val onGoogle: () -> Unit,
    val onCancel: () -> Unit,
    /** Customize was opened; it becomes the chosen preset. */
    val onCustomize: () -> Unit = {},
    /** Applies an edit to the lineup the Customize panel shows. */
    val onEditLineup: ((LineupEditor) -> LineupEditor) -> Unit = {},
)
