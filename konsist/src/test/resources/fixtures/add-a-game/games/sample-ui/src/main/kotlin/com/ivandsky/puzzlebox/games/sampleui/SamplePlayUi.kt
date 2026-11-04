package com.ivandsky.puzzlebox.games.sampleui

import com.ivandsky.puzzlebox.game.GameId
import com.ivandsky.puzzlebox.game.GamePlayController
import com.ivandsky.puzzlebox.game.GamePlayUi
import com.ivandsky.puzzlebox.games.sample.SampleDefinition

class SamplePlayUi : GamePlayUi {
    override val gameId: GameId get() = SampleDefinition.id

    override fun createController(): GamePlayController = SamplePlayController()
}
