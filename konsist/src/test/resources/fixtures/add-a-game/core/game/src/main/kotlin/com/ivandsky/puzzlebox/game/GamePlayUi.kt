package com.ivandsky.puzzlebox.game

interface GamePlayUi {
    val gameId: GameId

    fun createController(): GamePlayController
}
