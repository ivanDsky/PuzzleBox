package com.ivandsky.puzzlebox.game.editor

/**
 * Which grader labels a Unique board in [EditorDraft.analyze] (90). The caller picks it: a view
 * model, DI or the app graph reads the release flag and passes the mode down, so no analyzer or
 * grader ever reads a flag.
 */
enum class GradingMode {
    /** The label [GameEditor.analyze] gives: the hardest rung's level. Stored and received puzzles are always checked this way. */
    Legacy,

    /** The label of the editor's [ScoredGrader], when it has one; an editor without one keeps its legacy label. */
    Scored,
}
