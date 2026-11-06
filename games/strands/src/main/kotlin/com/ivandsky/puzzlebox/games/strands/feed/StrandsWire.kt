package com.ivandsky.puzzlebox.games.strands.feed

import kotlinx.serialization.Serializable

/**
 * The `svc/strands/v2/<date>.json` body (research 02). Fields are nullable so a missing one
 * reaches the trust gate as a typed rejection instead of a parse failure; `id`, `editor`,
 * `status` and `makeFree` are never read (A10) and are not modelled. Coordinates are
 * `[row, col]` pairs into `startingBoard[row][col]`.
 */
@Serializable
data class StrandsWire(
    val printDate: String? = null,
    val clue: String? = null,
    val constructors: String? = null,
    val spangram: String? = null,
    val themeWords: List<String>? = null,
    val startingBoard: List<String>? = null,
    val solutions: List<String>? = null,
    val themeCoords: Map<String, List<List<Int>>>? = null,
    val spangramCoords: List<List<Int>>? = null,
)
