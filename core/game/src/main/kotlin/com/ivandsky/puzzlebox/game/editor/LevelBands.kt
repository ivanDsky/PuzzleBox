package com.ivandsky.puzzlebox.game.editor

/**
 * A game's score thresholds (90): a score under [medium] is Easy, from [medium] Medium, from [hard]
 * Hard and from [expert] Expert. Each game tunes its own three numbers; every game reads them the
 * same way, so a grader and its completer never disagree on where a level starts.
 */
class LevelBands(private val medium: Int, private val hard: Int, private val expert: Int) {
    init {
        require(medium < hard && hard < expert) { "thresholds must rise: $medium, $hard, $expert" }
    }

    fun levelOf(score: Int): Difficulty.Level = when {
        score >= expert -> Difficulty.Level.Expert
        score >= hard -> Difficulty.Level.Hard
        score >= medium -> Difficulty.Level.Medium
        else -> Difficulty.Level.Easy
    }

    /** The scored label for [score]; [hardestRung] is null for a game without a ladder. */
    fun difficulty(score: Int, hardestRung: LadderRung? = null): Difficulty = Difficulty(levelOf(score), score, hardestRung)
}
