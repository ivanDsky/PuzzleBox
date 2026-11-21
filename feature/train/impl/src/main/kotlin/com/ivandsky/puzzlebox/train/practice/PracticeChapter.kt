package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique

/**
 * Where practice stands in a technique's chapter (spec §2.2): the [technique] being trained, which of its boards is up
 * ([board], counted from 1) and how many the chapter has ([boards]: 5, or 4 for the first technique of a game's catalog).
 */
internal data class PracticeChapter(val technique: TrainerTechnique, val board: Int, val boards: Int)
