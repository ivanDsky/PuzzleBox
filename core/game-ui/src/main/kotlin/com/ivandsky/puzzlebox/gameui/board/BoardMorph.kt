package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.Stable

/**
 * A board on its way between two, in the caller's hands: [progress] runs 0 at [from] to 1 at [to] (a drag, or the
 * spring that settles it). While a [DailyBoard] has one it draws both boards as one list: a line on both keeps one
 * row, which travels from its place on the one to its place on the other while its place rolls and its other cells
 * frost over, and a line on only one frosts out over the first half or in over the second.
 *
 * [progress] is read only where the list is laid out and drawn, never in composition, so moving it recomposes nothing.
 */
@Stable
class BoardMorph(val from: BoardFace, val to: BoardFace, val progress: () -> Float)
