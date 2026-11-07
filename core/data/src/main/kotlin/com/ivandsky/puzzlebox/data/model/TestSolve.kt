package com.ivandsky.puzzlebox.data.model

/** The author's completed test solve of a draft: [timeMs] and [hints], valid only for [layoutKey]. */
data class TestSolve(val layoutKey: String, val timeMs: Long, val hints: Int)
