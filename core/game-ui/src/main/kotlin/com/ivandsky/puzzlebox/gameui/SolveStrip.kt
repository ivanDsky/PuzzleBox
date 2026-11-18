package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.replay.MoveKind

internal const val STRIP_NORMAL = "🟩"
internal const val STRIP_UNDO = "🟨"
internal const val STRIP_HINT = "🟦"
internal const val STRIP_MISTAKE = "🟥"
internal const val STRIP_EMPTY = "⬜"

private const val SQUARES = 10

/**
 * Where in a solve you undid, erred or took a hint: [SQUARES] squares, one per tenth of [endMs]. Tick `i` (at
 * [ticksMs]`[i]`, of kind [kindsAt]`[i]`) falls in square `min(9, ticksMs[i] * 10 / endMs)`; a square shows the worst
 * [MoveKind] among its ticks and an untouched one is empty. It shows effort, never the board. Null with no ticks or no
 * time to divide.
 */
fun solveStrip(ticksMs: LongArray, kindsAt: Array<MoveKind>, endMs: Long): String? {
    if (ticksMs.isEmpty() || endMs <= 0) return null
    val worst = arrayOfNulls<MoveKind>(SQUARES)
    for (i in ticksMs.indices) {
        val square = (ticksMs[i] * SQUARES / endMs).coerceIn(0, SQUARES - 1L).toInt()
        val kind = kindsAt[i]
        if (worst[square].let { it == null || kind > it }) worst[square] = kind
    }
    return worst.joinToString("") { kind ->
        when (kind) {
            null -> STRIP_EMPTY
            MoveKind.Normal -> STRIP_NORMAL
            MoveKind.Undo -> STRIP_UNDO
            MoveKind.Hint -> STRIP_HINT
            MoveKind.Mistake -> STRIP_MISTAKE
        }
    }
}
