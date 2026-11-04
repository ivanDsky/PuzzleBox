package com.ivandsky.puzzlebox.games.sample.feed

import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.GameId
import com.ivandsky.puzzlebox.games.sample.SampleDefinition

class FixtureSampleDailySource : DailySource {
    override val gameId: GameId = SampleDefinition.id

    override suspend fun fetchDaily(date: String): String? = null
}
