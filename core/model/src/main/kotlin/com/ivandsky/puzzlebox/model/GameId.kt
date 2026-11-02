package com.ivandsky.puzzlebox.model

/** Stable cross-module identity of a game ("zip", "queens", ...). */
@JvmInline
value class GameId(val value: String) {
    override fun toString(): String = value
}
