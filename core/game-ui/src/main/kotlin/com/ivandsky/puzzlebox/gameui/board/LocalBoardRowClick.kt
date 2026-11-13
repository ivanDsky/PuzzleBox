package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What a tap on a PuzzleBox row does, when the board's caller opted in (see `DailyBoard`'s `onRowClick`). Null on every
 * board that did not, whose rows then take no tap. Only another player's official row is tappable.
 */
internal val LocalBoardRowClick = staticCompositionLocalOf<((DailyBoardRowModel) -> Unit)?> { null }
