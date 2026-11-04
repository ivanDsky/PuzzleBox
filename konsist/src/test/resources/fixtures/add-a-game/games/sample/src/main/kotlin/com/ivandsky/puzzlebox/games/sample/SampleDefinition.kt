package com.ivandsky.puzzlebox.games.sample

import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GameId

object SampleDefinition : GameDefinition {
    override val id = GameId("sample")
    override val displayName = "Sample"
}
