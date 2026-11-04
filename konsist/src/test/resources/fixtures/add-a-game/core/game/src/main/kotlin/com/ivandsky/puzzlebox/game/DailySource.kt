package com.ivandsky.puzzlebox.game

interface DailySource {
    val gameId: GameId

    suspend fun fetchDaily(date: String): String?
}
