package com.ivandsky.puzzlebox.game

/** The one type `app` aggregates per game; Koin registers it under the game's id. */
class GamePlugin(val definition: GameDefinition)
