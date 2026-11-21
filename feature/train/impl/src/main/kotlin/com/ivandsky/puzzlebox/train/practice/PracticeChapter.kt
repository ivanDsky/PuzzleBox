package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique

/** The focus boards every chapter starts with. */
private const val FOCUS_BOARDS = 4

/**
 * Where practice stands in a technique's chapter (spec §2.2): the [technique] being trained, which of its boards is up
 * ([board], counted from 1) and how many the chapter has ([boards]: 5, or 4 for the first technique of a game's catalog).
 */
internal data class PracticeChapter(val technique: TrainerTechnique, val board: Int, val boards: Int)

/** The kinds of board a chapter of the technique at catalog position [index] has, in order: the focus boards, then a review unless it is the catalog's first technique. */
internal fun chapterKinds(index: Int): List<BoardKind> =
    List(FOCUS_BOARDS) { BoardKind.Focus } + if (index == 0) emptyList() else listOf(BoardKind.Review)
