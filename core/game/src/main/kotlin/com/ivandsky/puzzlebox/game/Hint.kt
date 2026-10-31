package com.ivandsky.puzzlebox.game

/**
 * Marker for a game-authored hint (finding F3-①: trivially game-typed). Queens highlights a
 * whole region, Zip the next cell, Strands reveals letters — the presentation payload is the
 * game's own type. Core and :core:game-ui never interpret a [Hint]; they trigger, count (A3),
 * and pass it through to the game's renderer-model factory.
 */
interface Hint
