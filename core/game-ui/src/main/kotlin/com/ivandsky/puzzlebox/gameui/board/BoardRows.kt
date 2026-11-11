package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One item of the animated list: the [line] it last showed (null for the divider), whether it is the [pinned] own row,
 * and whether it is still [present] on the board or only closing before it is dropped. The three animated values are
 * read only where they are drawn or laid out, so moving them never recomposes the row: [open] is how much of the item's
 * height is there (0 to 1), [alpha] how visible it is, [slide] the distance in pixels it still has to travel to its place.
 * [arrived] says it joined a board that was already showing rows, and [delayMs] is how long it waits to start to move,
 * which its place and time cells wait too (see [PlaceMotion]).
 */
internal class BoardItem(
    val key: String,
    var line: DailyBoardLine?,
    var pinned: Boolean,
    var present: Boolean,
    override val arrived: Boolean = false,
) : PlaceMotion {
    override val animated: Boolean get() = true
    override var delayMs = 0

    val open = Animatable(0f)
    val alpha = Animatable(0f)
    val slide = Animatable(0f)

    // Where the item was last placed and how far it was then drawn from there, for the next move to start from.
    private var placedY = 0
    private var placed = false
    private var drawnSlide = 0f
    private var sliding: Job? = null

    // Set when the item's order among the items that stay has changed: its next placement is a jump to slide out of.
    var reordered = false

    /** Places the item at [y] in the list; if it was reordered, slides it there from where it was drawn, after its [delayMs]. */
    fun placeAt(y: Int, scope: CoroutineScope) {
        if (reordered && placed) {
            val from = placedY + drawnSlide - y
            drawnSlide = from
            if (from != 0f) {
                // Unconfined, so the row is drawn at its old place from the first frame, where it waits for its turn.
                sliding?.cancel()
                sliding = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    slide.snapTo(from)
                    delay(delayMs.toLong())
                    slide.animateTo(0f, BoardMotion.slide) { drawnSlide = value }
                }
            }
        }
        reordered = false
        placedY = y
        placed = true
    }
}

/**
 * The items the animated list draws: the current lines, in order, then the divider and the pinned row when there is
 * one, plus the ones that have just left, kept in place until their exit has played so there is something to close.
 * Kept across recompositions by the list.
 */
internal class BoardRows {
    private var items = emptyList<BoardItem>()

    // Set once the board has shown rows: from then on a row that joins it is an arrival, the first list's rows are not.
    private var shown = false

    // Counts the items dropped; update reads it, so dropping one recomposes the list.
    private var dropped by mutableIntStateOf(0)

    /** The items for [lines] and the [pinned] own row under them, leaving ones included. */
    fun update(lines: List<DailyBoardLine>, pinned: DailyBoardLine.Real?): List<BoardItem> {
        check(dropped >= 0)
        val before = items.associateBy { it.key }
        val all = lines + listOfNotNull(pinned)
        val keys = boardKeys(all, pinned != null)
        val wanted = ArrayList<BoardItem>(all.size + 1)
        fun take(key: String, line: DailyBoardLine?, isPinned: Boolean) {
            wanted += before[key]?.also {
                it.line = line
                it.pinned = isPinned
                it.present = true
            } ?: BoardItem(key, line, isPinned, present = true, arrived = shown)
        }
        lines.forEachIndexed { index, line -> take(keys[index], line, isPinned = false) }
        if (pinned != null) {
            take(DIVIDER_KEY, null, isPinned = false)
            take(keys.last(), pinned, isPinned = true)
        }
        markReordered(wanted)
        stagger(wanted, before.keys)
        shown = shown || wanted.isNotEmpty()
        items = withLeaving(wanted)
        return items
    }

    /** Drops [item] once it has closed. */
    fun drop(item: BoardItem) {
        items = items - item
        dropped++
    }

    // An item that stays but is no longer in the same order among the ones that stay has jumped; the rest only move
    // because items open and close around them, which the layout carries along.
    private fun markReordered(current: List<BoardItem>) {
        val inBoth = current.mapTo(HashSet()) { it.key }.also { keys -> keys.retainAll(items.mapTo(HashSet()) { it.key }) }
        val oldOrder = items.filter { it.key in inBoth }
        current.filter { it.key in inBoth }.forEachIndexed { index, item ->
            if (oldOrder[index].key != item.key) item.reordered = true
        }
    }

    // Rows set off one after another in the new order: a reordered row after its turn, an arriving one after the entrance
    // delay and its turn too. Rows that only shift because others open and close wait nothing, and neither does the divider's turn.
    private fun stagger(current: List<BoardItem>, before: Set<String>) {
        var turn = 0
        for (item in current) {
            val takesTurn = item.line != null
            item.delayMs = when {
                item.reordered -> BoardMotion.staggerMs(turn++)
                item.key !in before && item.arrived ->
                    BoardMotion.ArrivalDelayMillis + if (takesTurn) BoardMotion.staggerMs(turn++) else 0
                else -> 0
            }
        }
    }

    // A leaving item stays after the item that was ahead of it, so what remains closes up around it as it closes.
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
