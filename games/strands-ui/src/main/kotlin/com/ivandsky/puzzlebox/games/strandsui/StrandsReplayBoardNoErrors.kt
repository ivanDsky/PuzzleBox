package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard

/** Strands' replay board as the replay pages see it: a Strands replay can show no error, so Show errors is hidden. */
internal object StrandsReplayBoardNoErrors : GameReplayBoard by StrandsReplayBoard {
    override val hasErrors: Boolean = false
}
