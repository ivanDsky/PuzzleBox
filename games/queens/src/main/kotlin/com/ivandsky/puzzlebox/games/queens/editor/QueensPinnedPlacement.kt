package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import kotlin.math.abs
import kotlin.random.Random

/**
 * The answer one [QueensPinnedGrowth] attempt builds its board around, with the region of each
 * of its queens: one queen a row and a column, none touching, every crown among them. A queen
 * on a painted cell is its region's, so a region's cells hold one at most. A queen on an
 * unpainted cell goes to a started region that has none and can walk to it over unpainted
 * cells, or it seeds a region the author never started. With [seedsOnly] it only ever seeds: no
 * more queens lie on unpainted cells than there are regions to start, so every started region
 * holds its queen on its own paint.
 */
internal class QueensPinnedPlacement(
    private val draft: QueensDraft,
    private val neighbours: Array<IntArray>,
    private val budget: Budget,
    private val seedsOnly: Boolean = false,
) {
    private val n = draft.size
    private val started = draft.regions.filter { it != QueensDraft.UNPAINTED }.distinct()

    /**
     * The queen cell of every region id on [reg] (the draft's regions, each in one piece), or
     * null when no placement was found. Rows fill top down over a random column order,
     * backtracking; a branch ends at the last row a started region can reach when no queen
     * lies within that reach.
     */
    fun seats(reg: IntArray, random: Random): IntArray? {
        val column = IntArray(n) { NONE }
        val columnTaken = BooleanArray(n)
        val held = IntArray(n)
        var seeds = if (seedsOnly) n - started.size else n
        for (crown in draft.crowns) {
            column[crown / n] = crown % n
            columnTaken[crown % n] = true
            if (reg[crown] != QueensDraft.UNPAINTED && ++held[reg[crown]] > 1) return null
            if (reg[crown] == QueensDraft.UNPAINTED && --seeds < 0) return null
        }
        val reachedBy = IntArray(n * n) // per cell, one bit for every region whose reach holds it
        val due = List(n) { ArrayList<Int>() }
        for (region in started) {
            val reach = reg.indices.filter { reg[it] == region } + open(reg, region, queens = null)
            for (cell in reach) reachedBy[cell] = reachedBy[cell] or (1 shl region)
            due[reach.max() / n] += region
        }
        var nodes = 0
        var spent = false

        fun place(row: Int): IntArray? {
            if (row == n) return assign(reg, column, random)
            val crowned = column[row] != NONE
            val order = if (crowned) intArrayOf(column[row]) else IntArray(n) { it }.also { it.shuffle(random) }
            for (col in order) {
                spent = spent || ++nodes > MAX_NODES || (nodes % POLL_INTERVAL == 0 && budget.exhausted())
                if (spent) return null
                val region = reg[row * n + col]
                if (!crowned) {
                    if (columnTaken[col]) continue
                    if (region != QueensDraft.UNPAINTED && held[region] > 0) continue
                    if (region == QueensDraft.UNPAINTED && seeds == 0) continue
                }
                if (row > 0 && abs(column[row - 1] - col) <= 1) continue
                if (row < n - 1 && column[row + 1] != NONE && abs(column[row + 1] - col) <= 1) continue
                if (!crowned) {
                    column[row] = col
                    columnTaken[col] = true
                    if (region != QueensDraft.UNPAINTED) held[region]++ else seeds--
                }
                val inReach = due[row].all { late -> (0..row).any { reachedBy[it * n + column[it]] shr late and 1 == 1 } }
                if (inReach) place(row + 1)?.let { return it }
                if (!crowned) {
                    column[row] = NONE
                    columnTaken[col] = false
                    if (region != QueensDraft.UNPAINTED) held[region]-- else seeds++
                }
            }
            return null
        }
        return place(0)
    }

    /**
     * Shares the queens out. A started region without a queen on its cells takes one it can walk
     * to, the nearest still free, and a region that finds none makes an earlier one move on.
     * The queens left over seed the unstarted regions. Null when a started region gets none.
     */
    private fun assign(reg: IntArray, column: IntArray, random: Random): IntArray? {
        val isQueen = BooleanArray(n * n)
        val seat = IntArray(n) { NONE }
        for (row in 0 until n) {
            val cell = row * n + column[row]
            isQueen[cell] = true
            if (reg[cell] != QueensDraft.UNPAINTED) seat[reg[cell]] = cell
        }
        val wanting = started.filter { seat[it] == NONE }.shuffled(random)
        val reachable = wanting.associateWith { region -> open(reg, region, isQueen).filter { isQueen[it] } }
        val taker = HashMap<Int, Int>()

        fun take(region: Int, asked: HashSet<Int>): Boolean {
            for (queen in reachable.getValue(region)) {
                if (!asked.add(queen)) continue
                val holder = taker[queen]
                if (holder == null || take(holder, asked)) {
                    taker[queen] = region
                    seat[region] = queen
                    return true
                }
            }
            return false
        }
        for (region in wanting) if (!take(region, HashSet())) return null
        val seeds = isQueen.indices.filter { isQueen[it] && reg[it] == QueensDraft.UNPAINTED && it !in taker }
        (0 until n).filter { it !in started }.shuffled(random).forEachIndexed { index, region -> seat[region] = seeds[index] }
        return seat
    }

    /**
     * The unpainted cells [region] can walk to over unpainted cells, nearest first. With
     * [queens], a queen's cell is listed and not walked through.
     */
    private fun open(reg: IntArray, region: Int, queens: BooleanArray?): List<Int> {
        val seen = BooleanArray(n * n)
        val queue = ArrayDeque<Int>()
        for (cell in reg.indices) if (reg[cell] == region) queue.addLast(cell)
        val open = ArrayList<Int>()
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (reg[cell] == QueensDraft.UNPAINTED) {
                open += cell
                if (queens?.get(cell) == true) continue
            }
            for (next in neighbours[cell]) {
                if (reg[next] == QueensDraft.UNPAINTED && !seen[next]) {
                    seen[next] = true
                    queue.addLast(next)
                }
            }
        }
        return open
    }

    private companion object {
        const val NONE = -1

        /** Placement nodes per attempt; past them a fresh column order beats backtracking on. */
        const val MAX_NODES = 20_000

        /** Placement nodes between budget polls. */
        const val POLL_INTERVAL = 1024
    }
}
