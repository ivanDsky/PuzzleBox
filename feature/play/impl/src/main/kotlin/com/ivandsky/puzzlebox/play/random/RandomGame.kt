package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

/** A game the sheet's chips offer: its [id], the [name] on the chip and the [brand] its glyph is drawn from. */
internal data class RandomGame(val id: GameId, val name: String, val brand: GameBrand)
