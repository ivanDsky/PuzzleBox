package com.ivandsky.puzzlebox.game

/** Marker for a game-authored move value (Zip's Extend/RetractTo/Reset, a Strands word trace). */
interface Move

/** Marker for a game-authored validation outcome (Zip's Valid / Invalid(reason)). */
interface MoveVerdict

/**
 * Pure rules for one game (F1 §2). [P] is the game's rules state.
 *
 * Hints are counted by the caller (A3) — rules never track hint usage themselves.
 * A new rule, special case, or mechanic post-release lands as a new variant in the
 * game's own module (A18): these three signatures never grow.
 */
interface GameRules<P> {
    fun validate(move: Move, state: P): MoveVerdict

    fun isComplete(state: P): Boolean

    fun hint(state: P): Hint
}
