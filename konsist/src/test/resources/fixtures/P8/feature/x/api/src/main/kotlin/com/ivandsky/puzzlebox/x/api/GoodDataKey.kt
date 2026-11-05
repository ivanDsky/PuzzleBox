package com.ivandsky.puzzlebox.x.api

import androidx.navigation3.runtime.NavKey

data class GoodDataKey(
    val gameId: String,
    val day: String? = null,
    val timeMs: Long,
    val hintsUsed: Int,
    val hard: Boolean,
) : NavKey
