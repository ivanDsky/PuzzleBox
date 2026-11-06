package com.ivandsky.puzzlebox.game.editor

/** A puzzle's difficulty label: the level of the hardest ladder rung its solve needs. */
data class Difficulty(val hardestRung: LadderRung) {
    val level: Level get() = hardestRung.level
    enum class Level { Easy, Medium, Hard, Expert }
}
