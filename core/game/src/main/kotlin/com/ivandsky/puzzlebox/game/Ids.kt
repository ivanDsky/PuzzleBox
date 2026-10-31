package com.ivandsky.puzzlebox.game

/** Stable cross-module identity of a game ("zip", "queens", ...). */
@JvmInline
value class GameId(val value: String) {
    override fun toString(): String = value
}

/**
 * Pins the rule regime a puzzle was authored for (A18 / ADR-0005), e.g. "zip/n6".
 * Matched exactly against the owning game's variant table — never parsed for meaning.
 * Old puzzles keep their variant and stay valid forever; a new rule regime is a new
 * [VariantId] inside the game's own module, never a core change.
 */
@JvmInline
value class VariantId(val value: String) {
    override fun toString(): String = value
}
