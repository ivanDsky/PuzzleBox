package com.ivandsky.puzzlebox.games.strands.engine.tiling

import com.ivandsky.puzzlebox.game.editor.Budget

/**
 * Exact cover of the board by word placements. A placement is the cell set one 8-neighbour path of a
 * word occupies; paths over the same cells are one placement, since a tiling only records which word
 * owns each cell. Cells are bits of a [Long], so a board holds at most 64 cells.
 */
object StrandsTilingSearch {
    /** Tilings of [letters] (rows × cols) by [words], each an 8-neighbour path, disjoint, covering every cell.
     *  [first], when given, is tried first so the author's tiling is witness A. */
    fun bounded(rows: Int, cols: Int, letters: String, words: List<String>, first: List<Int>?, limit: Int, budget: Budget): StrandsTilingOutcome {
        require(limit > 0) { "limit must be positive: $limit" }
        val board = Board(rows, cols, letters)
        if (words.sumOf { it.length } != letters.length) return StrandsTilingOutcome.Done(emptyList())
        val byText = HashMap<String, LongArray>()
        for (text in words.distinct()) {
            byText[text] = PlacementWalk(board, text, budget).all() ?: return StrandsTilingOutcome.Exhausted
        }
        val placements = words.map { checkNotNull(byText[it]) }
        return Cover(board.cells, placements, first, limit, budget).run()
    }

    /** Cells no placement of any word can cover, or null once [budget] ran out mid-walk. */
    fun uncoverable(
        rows: Int,
        cols: Int,
        letters: String,
        words: List<String>,
        budget: Budget = Budget.Unlimited,
    ): Set<Int>? {
        val board = Board(rows, cols, letters)
        val texts = words.distinct().filter(board::hasLettersFor)
        var covered = 0L
        for (cell in 0 until board.cells) {
            if (covered.has(cell)) continue
            covered = covered or (texts.firstNotNullOfOrNull { board.pathThrough(it, cell, budget) } ?: 0L)
            if (budget.exhausted()) return null
        }
        return (0 until board.cells).filterNot(covered::has).toSet()
    }
}

private fun Long.has(cell: Int): Boolean = this ushr cell and 1L == 1L

private fun bit(cell: Int): Long = 1L shl cell

private class Board(rows: Int, cols: Int, val letters: String) {
    val cells: Int = rows * cols

    init {
        require(letters.length == cells) { "letters must fill $rows × $cols, got ${letters.length}" }
        require(cells <= Long.SIZE_BITS) { "at most ${Long.SIZE_BITS} cells, got $cells" }
    }

    val neighbours: Array<IntArray> = Array(cells) { cell ->
        val row = cell / cols
        val col = cell % cols
        (-1..1).flatMap { dr -> (-1..1).map { dc -> dr to dc } }
            .filter { (dr, dc) -> (dr != 0 || dc != 0) && row + dr in 0 until rows && col + dc in 0 until cols }
            .map { (dr, dc) -> (row + dr) * cols + col + dc }
            .toIntArray()
    }

    private val letterCounts: Map<Char, Int> = letters.groupingBy { it }.eachCount()

    /** A word needing more of a letter than the board has fits nowhere; checked before any walk. */
    fun hasLettersFor(word: String): Boolean =
        word.groupingBy { it }.eachCount().all { (letter, count) -> count <= (letterCounts[letter] ?: 0) }

    /** Cells of some path of [word] through [cell], or null when there is none or it spends. */
    fun pathThrough(word: String, cell: Int, budget: Budget): Long? =
        word.indices.filter { word[it] == letters[cell] }
            .firstNotNullOfOrNull { index -> extend(word, cell, bit(cell), cell, index, forward = true, budget) }

    /** Grows the path forwards from [start] to the word's end, then backwards from [start] to its first letter. */
    private fun extend(
        word: String,
        start: Int,
        visited: Long,
        at: Int,
        index: Int,
        forward: Boolean,
        budget: Budget,
    ): Long? {
        if (budget.exhausted()) return null
        if (forward && index == word.lastIndex) return extendBack(word, start, visited, budget)
        if (!forward && index == 0) return visited
        val next = if (forward) index + 1 else index - 1
        for (neighbour in neighbours[at]) {
            if (visited.has(neighbour) || letters[neighbour] != word[next]) continue
            extend(word, start, visited or bit(neighbour), neighbour, next, forward, budget)?.let { return it }
        }
        return null
    }

    /** The forward leg covered [start]'s letter through the last one, so the path length gives [start]'s index. */
    private fun extendBack(word: String, start: Int, visited: Long, budget: Budget): Long? =
        extend(word, start, visited, start, word.length - visited.countOneBits(), forward = false, budget)
}

/** Every distinct placement of one word, or null once the budget runs out mid-walk. */
private class PlacementWalk(private val board: Board, private val word: String, private val budget: Budget) {
    private val found = LinkedHashSet<Long>()
    private var steps = 0
    private var exhausted = false

    fun all(): LongArray? {
        if (board.hasLettersFor(word)) {
            for (cell in 0 until board.cells) {
                if (board.letters[cell] == word[0]) walk(cell, bit(cell), 0)
                if (exhausted) return null
            }
        }
        return found.toLongArray()
    }

    private fun walk(at: Int, visited: Long, index: Int) {
        if (++steps % POLL_EVERY == 0 && budget.exhausted()) exhausted = true
        if (exhausted) return
        if (index == word.lastIndex) {
            found += visited
            return
        }
        for (neighbour in board.neighbours[at]) {
            if (!visited.has(neighbour) && board.letters[neighbour] == word[index + 1]) {
                walk(neighbour, visited or bit(neighbour), index + 1)
            }
        }
    }

    private companion object {
        const val POLL_EVERY = 1024
    }
}

/**
 * Depth-first exact cover: branch on the uncovered cell with the fewest live placements, and at each
 * cell try the author's placement first so an intact author tiling is always found first.
 */
private class Cover(
    private val cells: Int,
    placements: List<LongArray>,
    first: List<Int>?,
    private val limit: Int,
    private val budget: Budget,
) {
    private val wordCount = placements.size
    private val wordOf: IntArray = placements.flatMapIndexed { word, masks -> List(masks.size) { word } }.toIntArray()
    private val maskOf: LongArray = placements.flatMap { it.asList() }.toLongArray()
    private val authorCell: List<Int>? = first?.takeIf { it.size == cells }
    private val authorMask: LongArray = LongArray(wordCount).also { masks ->
        authorCell?.forEachIndexed { cell, word -> if (word in 0 until wordCount) masks[word] = masks[word] or bit(cell) }
    }
    private val full: Long = if (cells == Long.SIZE_BITS) -1L else bit(cells) - 1
    private val chosen = IntArray(wordCount)
    private val used = BooleanArray(wordCount)
    private val levels = arrayOfNulls<IntArray>(wordCount + 1)
    private val tilings = ArrayList<List<Int>>()
    private var exhausted = false

    fun run(): StrandsTilingOutcome {
        val live = IntArray(maskOf.size) { it }
        search(live, live.size, covered = 0L, depth = 0)
        return if (exhausted) StrandsTilingOutcome.Exhausted else StrandsTilingOutcome.Done(tilings.toList())
    }

    private fun search(live: IntArray, liveSize: Int, covered: Long, depth: Int) {
        if (budget.exhausted()) exhausted = true
        if (exhausted) return
        if (covered == full) {
            record(depth)
            return
        }
        val cell = branchCell(live, liveSize, covered) ?: return
        val author = authorPlacement(live, liveSize, cell)
        if (author >= 0) place(author, live, liveSize, covered, depth)
        for (i in 0 until liveSize) {
            if (tilings.size == limit || exhausted) return
            val placement = live[i]
            if (placement != author && maskOf[placement].has(cell)) place(placement, live, liveSize, covered, depth)
        }
    }

    private fun place(placement: Int, live: IntArray, liveSize: Int, covered: Long, depth: Int) {
        val word = wordOf[placement]
        val mask = maskOf[placement]
        val next = level(depth + 1, liveSize)
        var nextSize = 0
        for (i in 0 until liveSize) {
            val other = live[i]
            if (wordOf[other] != word && maskOf[other] and mask == 0L) next[nextSize++] = other
        }
        chosen[depth] = placement
        used[word] = true
        search(next, nextSize, covered or mask, depth + 1)
        used[word] = false
    }

    /** The uncovered cell with the fewest live placements, or null when a cell or an unused word has none. */
    private fun branchCell(live: IntArray, liveSize: Int, covered: Long): Int? {
        val perCell = IntArray(cells)
        val perWord = IntArray(wordCount)
        for (i in 0 until liveSize) {
            val placement = live[i]
            perWord[wordOf[placement]]++
            var mask = maskOf[placement]
            while (mask != 0L) {
                perCell[mask.countTrailingZeroBits()]++
                mask = mask and (mask - 1)
            }
        }
        if ((0 until wordCount).any { !used[it] && perWord[it] == 0 }) return null
        val cell = (0 until cells).filterNot(covered::has).minBy { perCell[it] }
        return cell.takeIf { perCell[it] > 0 }
    }

    private fun authorPlacement(live: IntArray, liveSize: Int, cell: Int): Int {
        val word = authorCell?.get(cell) ?: return -1
        if (word !in 0 until wordCount) return -1
        for (i in 0 until liveSize) {
            val placement = live[i]
            if (wordOf[placement] == word && maskOf[placement] == authorMask[word]) return placement
        }
        return -1
    }

    private fun level(depth: Int, size: Int): IntArray {
        val current = levels[depth]
        if (current != null && current.size >= size) return current
        return IntArray(size).also { levels[depth] = it }
    }

    private fun record(depth: Int) {
        val tiling = MutableList(cells) { -1 }
        for (d in 0 until depth) {
            val placement = chosen[d]
            for (cell in 0 until cells) if (maskOf[placement].has(cell)) tiling[cell] = wordOf[placement]
        }
        tilings += tiling
    }
}
