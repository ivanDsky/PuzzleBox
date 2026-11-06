package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** A game History can open past days of (A12): its id, name, brand and the days its archive spans. */
data class ArchiveGame(
    val gameId: GameId,
    val name: String,
    val brand: GameBrand,
    val range: ClosedRange<LocalDate>,
)
