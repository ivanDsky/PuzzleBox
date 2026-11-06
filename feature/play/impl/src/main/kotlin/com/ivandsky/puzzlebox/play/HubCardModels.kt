package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.gameui.GameCardModel

/** This row's card inputs, copied field for field: every card formats its own text. */
internal fun HubRow.toCardModel(): GameCardModel = GameCardModel(
    game = game,
    name = name,
    brand = brand,
    status = status,
    rankOrder = rankOrder,
    puzzleNumber = puzzleNumber,
    best = best,
    line = line,
)
