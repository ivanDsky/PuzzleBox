package com.ivandsky.puzzlebox.gameui

/**
 * The card's "#N · Best X" line; either part drops when absent, and null when both are.
 * The best reads in the game's own rank order via [bestScoreText].
 */
fun cardMeta(model: GameCardModel): String? = listOfNotNull(
    model.puzzleNumber?.let { "#$it" },
    model.best?.let { "Best ${bestScoreText(it, model.rankOrder)}" },
).joinToString(" · ").ifEmpty { null }
