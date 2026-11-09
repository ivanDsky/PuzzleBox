package com.ivandsky.puzzlebox.model

/**
 * A game's own boolean setting. The game owns the [name]; core stores and observes the
 * value per game without ever naming a game.
 */
data class GameSetting(val game: GameId, val name: String, val default: Boolean) {
    init {
        require(name.matches(NAME)) { "Setting name must match [a-z0-9_]+: $name" }
    }

    private companion object {
        val NAME = Regex("[a-z0-9_]+")
    }
}
