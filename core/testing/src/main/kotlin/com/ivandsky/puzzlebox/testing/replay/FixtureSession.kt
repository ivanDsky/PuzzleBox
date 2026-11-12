package com.ivandsky.puzzlebox.testing.replay

import com.ivandsky.puzzlebox.game.GameSession

/** Session of the fake replay game: pieces placed, out of [total], and hints taken. */
data class FixtureSession(val placed: Int, val total: Int, val hints: Int) : GameSession
