package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

internal val ZipId = GameId("zip")
internal val TangoId = GameId("tango")

/** Zip solved in 0:42 with no hints, the one-game day most Home tests need. */
internal fun solvedZip() = HistoryDay.GameResult(ZipId, "Zip", GameBrand.Neutral, HistoryDay.Outcome.Solved(42_000L, hints = 0))

/** Tango opened but not solved: its Recent chip reads "–". */
internal fun openedTango() = HistoryDay.GameResult(TangoId, "Tango", GameBrand.Neutral, HistoryDay.Outcome.Opened)
