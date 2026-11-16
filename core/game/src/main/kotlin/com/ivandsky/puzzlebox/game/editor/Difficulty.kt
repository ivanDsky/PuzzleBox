package com.ivandsky.puzzlebox.game.editor

/**
 * A puzzle's difficulty label: its [level], the [score] of the whole solve the level was read from
 * and, where the game has a ladder, the [hardestRung] the solve needs.
 *
 * Two graders make one (90). A game's legacy grader labels by the hardest rung alone and leaves
 * [score] null: build it with the rung constructor. Its [ScoredGrader] weighs the whole solve and
 * sets [score], with [level] taken from the game's [LevelBands]; [hardestRung] is null for a game
 * without a ladder (Zip). Which one a caller sees is its [GradingMode].
 */
data class Difficulty(val level: Level, val score: Int?, val hardestRung: LadderRung?) {
    /** The legacy label: the level of the hardest ladder rung the solve needs, and no score. */
    constructor(hardestRung: LadderRung) : this(hardestRung.level, score = null, hardestRung = hardestRung)

    enum class Level { Easy, Medium, Hard, Expert }
}
