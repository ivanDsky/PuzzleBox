package com.ivandsky.puzzlebox.game.linkedin

/** One cell the player filled, as LinkedIn's voyager save stores it: the row-major [idx] and the [value] in it. */
data class LinkedInVoyagerCell(val idx: Int, val value: Int)
