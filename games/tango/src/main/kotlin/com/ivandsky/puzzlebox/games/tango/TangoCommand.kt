package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.Command

/** One reversible Tango edit over the player overlay: a single-cell [TangoPlace] or a whole-board [TangoClear]. */
sealed interface TangoCommand : Command<TangoPlacements>
