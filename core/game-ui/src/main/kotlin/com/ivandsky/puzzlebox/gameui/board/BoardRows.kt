package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * One item of the animated list: the line it last showed ([pinned] when it is the own row under its divider), and
 * whether it is still [present] on the board or only shrinking out before it is dropped.
 */
internal class BoardItem(val key: String, var line: DailyBoardLine, var pinned: Boolean, var present: Boolean)

/**
 * The items the animated list draws: the current lines, in order, plus the ones that have just left, kept in place
 * until their exit has played so there is something to shrink. Kept across recompositions by the list.
 */
internal class BoardRows {
    private var items = emptyList<BoardItem>()

    // Counts the items dropped; update reads it, so dropping one recomposes the list.
    private var dropped by mutableIntStateOf(0)

    /** The items for [keys] and their [lines] (the pinned one last, when there is one), leaving ones included. */
    fun update(keys: List<String>, lines: List<DailyBoardLine>, hasPinned: Boolean): List<BoardItem> {
        check(dropped >= 0)
        val before = items.associateBy { it.key }
        val current = keys.mapIndexed { index, key ->
            val pinned = hasPinned && index == keys.lastIndex
            before[key]?.also {
                it.line = lines[index]
                it.pinned = pinned
                it.present = true
            } ?: BoardItem(key, lines[index], pinned, present = true)
        }
        items = withLeaving(current)
        return items
    }

    /** Drops [item] once it has shrunk out. */
    fun drop(item: BoardItem) {
        items = items - item
        dropped++
    }

    // A leaving item stays after the item that was ahead of it, so what remains closes up around it as it shrinks.
    private fun withLeaving(current: List<BoardItem>): List<BoardItem> {
        val staying = current.mapTo(HashSet()) { it.key }
        val merged = current.toMutableList()
        items.forEachIndexed { index, item ->
            if (item.key in staying) return@forEachIndexed
            item.present = false
            val anchor = (index - 1 downTo 0).firstNotNullOfOrNull { ahead -> merged.indexOf(items[ahead]).takeIf { it >= 0 } }
            merged.add((anchor ?: -1) + 1, item)
        }
        return merged
    }
}
