package com.ivandsky.puzzlebox.game.editor

/** Why a board is Incomplete: [id] is a stable machine id; copy lives in the game UI. */
data class IncompleteReason(val id: String, val count: Int)
