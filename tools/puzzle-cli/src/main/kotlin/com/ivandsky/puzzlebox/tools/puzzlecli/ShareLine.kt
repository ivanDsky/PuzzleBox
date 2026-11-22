package com.ivandsky.puzzlebox.tools.puzzlecli

import kotlinx.serialization.Serializable

/** The one JSON line `puzzle-cli share` prints: what the community document needs from the board (payload is base64). */
@Serializable
data class ShareLine(
    val game: String,
    val variant: String,
    val shareCodecVersion: Int,
    val payload: String,
    val difficulty: String?,
)
