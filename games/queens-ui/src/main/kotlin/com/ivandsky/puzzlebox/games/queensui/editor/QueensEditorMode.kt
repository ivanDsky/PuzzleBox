package com.ivandsky.puzzlebox.games.queensui.editor

/**
 * What a board tap does in the Queens editor (flow C, round 6): [Crown] places or removes an
 * answer crown, [Paint] selects cells for the colour ring. Not persisted. A draft opens in
 * Crown when no cell outside the crowns is painted (a new draft), else Paint.
 */
enum class QueensEditorMode { Crown, Paint }
