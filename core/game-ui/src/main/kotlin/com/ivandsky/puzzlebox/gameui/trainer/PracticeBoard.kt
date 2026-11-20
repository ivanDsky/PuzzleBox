package com.ivandsky.puzzlebox.gameui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import kotlinx.coroutines.flow.StateFlow

/**
 * One live practice board (spec §2.2): a drill puzzle opened just before [technique] is first the
 * easiest move, played to the end with the game's normal gestures. Each move is judged against the
 * easiest rung that applies at that moment; a move that is not Right is undone at once. Opaque to the
 * Train feature, which draws it through [TrainerGame.Board] and reads [status].
 */
interface PracticeBoard {
    /** The technique this board trains. */
    val technique: TechniqueId

    /** What the status band shows: the last move's outcome, or the standing instruction. */
    val status: StateFlow<PracticeStatus>

    /** Makes the easiest step on the board and reports it as [PracticeStatus.Shown]. Does nothing while an explanation stands. */
    fun showMe()

    /**
     * Dismisses a standing explanation ([PracticeStatus.Easier] or [PracticeStatus.Shown]): the status becomes
     * [PracticeStatus.FindNextMove] and the board takes moves again. Does nothing otherwise.
     */
    fun gotIt()
}
