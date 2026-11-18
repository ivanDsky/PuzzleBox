package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.LibraryRowModel

/** A game the sheet's chips offer: its [id], the [name] on the chip and the [brand] its glyph is drawn from. */
internal data class RandomGame(val id: GameId, val name: String, val brand: GameBrand)

/** The chips Past puzzles offers: the library rows that have an archive, in list order. */
internal fun List<LibraryRowModel>.archiveGames(): List<RandomGame> =
    filter { it.progress != null }.map { RandomGame(it.card.game, it.card.name, it.card.brand) }
