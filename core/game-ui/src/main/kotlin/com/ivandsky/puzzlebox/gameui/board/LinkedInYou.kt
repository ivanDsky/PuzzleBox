package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore

/**
 * What LinkedIn knows of the player on a day's board: the imported [score] (null while none), their LinkedIn [name] and
 * the absolute path of their cached [photo]. A null part is simply unknown.
 */
data class LinkedInYou(val score: BestScore?, val name: String?, val photo: String?)
