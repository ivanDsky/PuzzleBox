package com.ivandsky.puzzlebox.gameui.board

/**
 * Between two values of a [RollingNumber]: where the roll starts and how far along it is.
 *
 * @property from the number the roll leaves.
 * @property progress how far along the roll is, 0 to 1; read only where the roll is laid out, so a frame of it
 * recomposes nothing.
 */
class RollScrub(val from: Int, val progress: () -> Float)
