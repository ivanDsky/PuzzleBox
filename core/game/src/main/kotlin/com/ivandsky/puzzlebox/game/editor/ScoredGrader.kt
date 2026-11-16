package com.ivandsky.puzzlebox.game.editor

/**
 * Capability (ADR-0019): a [GameEditor] that grades a board by the score of its whole solve (90),
 * beside the legacy label its [GameEditor.analyze] gives. Callers find it with `as?`, so a game
 * without one keeps its legacy label. Pure and blocking; no dispatcher (Konsist P7) and no flags:
 * [EditorDraft.analyze] calls it only under [GradingMode.Scored].
 *
 * A game plugs in with one grader object, delegated on its editor
 * (`ScoredGrader<XDraft> by XScoredGrader`); its analyzer is not touched. The grader turns its score
 * into a level through one [LevelBands], which the game's completer reads too when it aims for a
 * target ([DraftCompleter.complete]).
 */
interface ScoredGrader<D : Any> {
    /**
     * The scored difficulty of [draft], a board whose analysis is Unique with [solution] and does
     * not need guessing. Polls [budget] in its loop; null when it is spent, and the board then
     * keeps its legacy label ([EditorDraft.analyze]).
     */
    fun grade(draft: D, solution: Solution, budget: Budget): Difficulty?
}
