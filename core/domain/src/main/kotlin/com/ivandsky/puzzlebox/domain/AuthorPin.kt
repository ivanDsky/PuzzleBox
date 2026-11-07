package com.ivandsky.puzzlebox.domain

/** A friend puzzle's author as its board pins them above the ranks: their test solve's time and hints. */
data class AuthorPin(val uid: String, val name: String, val timeMs: Long, val hints: Int)
