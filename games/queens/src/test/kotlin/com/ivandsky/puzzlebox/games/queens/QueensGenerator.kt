package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Random uniquely-solvable boards for property tests and the golden/benchmark workload
 * (zip precedent: ZipGenerator is test-only — production puzzles come from the daily
 * fetch). A13 hard gate: EXACTLY ONE solution, reached by REPAIR, not luck:
 *
 * 1. Random valid placement (the intended solution) + regions grown outward from the
 *    queens (compact regions constrain harder).
 * 2. Repair loop: solve up to 2. The intended placement ALWAYS stays valid (repairs only
 *    ever reassign cells that are not its queens), so if the count is 1, the unique
 *    solution IS the intended one. Otherwise take the second solution s2, pick a queen
 *    cell x of s2 whose region holds exactly one s2-queen, and reassign x to the region
 *    of the nearest intended queen — that region loses its only s2 queen, so s2 is dead.
 *    Each step kills at least one alternative; the loop converges well under budget at
 *    the official sizes (n8 ~50, n9 ~100, n10 ~200 repair steps measured).
 * 3. Stuck or over-budget attempts return null; [nextUnique] retries.
 *
 * Deterministic for a given [Random] seed, so failures and golden snapshots reproduce.
 * Parameterized (A9): any (size, queensPerUnit) configuration; official defaults are
 * N=1 at sizes 8/9/10 (research 01).
 */
class QueensGenerator(private val random: Random = Random.Default) {

    private val solver = QueensSolver()

    /** One generation attempt; null when the repair loop got stuck or ran out of budget. */
    fun next(size: Int, queensPerUnit: Int = 1): QueensPuzzle? {
        require(size > 0) { "size must be positive, got $size" }
        require(queensPerUnit > 0) { "queensPerUnit must be positive, got $queensPerUnit" }
        val n = size
        val q = queensPerUnit
        val placement = randomPlacement(n, q) ?: return null
        val regions = growRegions(n, q, placement) ?: return null
        val repaired = repair(n, q, placement, regions) ?: return null

        val puzzle = QueensPuzzle(
            size = n,
            regions = repaired.toList(),
            queensPerUnit = q,
            solution = placement.map { Cell(it) },
        )
        // A13 gate, defensive: the repair invariant guarantees the intended placement is
        // the unique solution — verify rather than trust.
        return if (solver.countSolutions(puzzle, limit = 2) == 1 &&
            solver.verifyPlacement(puzzle, puzzle.solution!!)
        ) {
            puzzle
        } else {
            null
        }
    }

    /** Retries until a unique board appears or [maxAttempts] run out. */
    fun nextUnique(size: Int, queensPerUnit: Int = 1, maxAttempts: Int = 40): QueensPuzzle? {
        repeat(maxAttempts) {
            next(size, queensPerUnit)?.let { return it }
        }
        return null
    }

    /**
     * The repair loop (see class doc). Returns the final region map, or null when stuck
     * (no killing candidate for the current alternative) or over [maxRepairSteps].
     */
    private fun repair(n: Int, q: Int, placement: List<Int>, regions: IntArray): IntArray? {
        val intended = placement.toSet()
        val reg = regions.copyOf()
        val dists = placement.map { bfsDistances(it, n) } // per intended queen
        repeat(MAX_REPAIR_STEPS) {
            val puzzle = QueensPuzzle(n, reg.toList(), q)
            val solutions = solver.solutionsUpTo(puzzle, limit = 2)
            if (solutions.size == 1) return reg
            val anchorIdx = solutions.firstOrNull { solution ->
                solution.map { c -> c.idx }.toSet() != intended
            }?.map { it.idx } ?: return null // both recorded solutions are the intended one (defensive)
            // Killing candidates: alternative queens whose region holds exactly one
            // alternative queen — reassignment empties that region under the alternative.
            val countsByRegion = HashMap<Int, Int>()
            for (cell in anchorIdx) countsByRegion.merge(reg[cell], 1) { a, b -> a + b }
            val candidates = anchorIdx.filter { it !in intended && countsByRegion[reg[it]] == 1 }
            if (candidates.isEmpty()) return null
            val x = candidates[random.nextInt(candidates.size)]
            // Target: the region of the nearest intended queen (ties: random among nearest),
            // never x's own region.
            val own = reg[x]
            val nearest = dists.minOf { it[x] }
            val targets = placement.filterIndexed { i, _ -> dists[i][x] == nearest }
                .map { reg[it] }
                .filter { it != own }
                .ifEmpty { (0 until n).filter { it != own } }
            reg[x] = targets[random.nextInt(targets.size)]
        }
        return null
    }

    /**
     * Random valid placement: q queens per row (columns count-constrained, same-row
     * spacing >= 2, no-touch vs the previous row), random column order with backtracking.
     */
    private fun randomPlacement(n: Int, q: Int): List<Int>? {
        val colCount = IntArray(n)
        val queens = ArrayList<Int>(n * q)
        val order = IntArray(n) { it }

        fun shuffle(): Unit = run {
            for (i in n - 1 downTo 1) {
                val j = random.nextInt(i + 1)
                order[i] = order[j].also { order[j] = order[i] }
            }
        }

        fun pick(startCol: Int, chosenInRow: Int, row: Int): Boolean {
            if (chosenInRow == q) {
                return row == n - 1 || pick(0, 0, row + 1)
            }
            shuffle()
            for (k in 0 until n) {
                val c = order[k]
                if (c < startCol) continue
                if (colCount[c] >= q) continue
                if (row > 0 && queens.any { prev -> prev / n == row - 1 && kotlin.math.abs(prev % n - c) <= 1 }) continue
                colCount[c]++
                queens.add(row * n + c)
                val ok = pick(c + 2, chosenInRow + 1, row)
                if (ok) return true
                queens.removeAt(queens.lastIndex)
                colCount[c]--
            }
            return false
        }
        return if (pick(0, 0, 0)) queens.toList() else null
    }

    /**
     * Region growth: seeds first (region i owns the queen cells of band i), then cells are
     * claimed from the frontiers, smallest region first, random cell within it — compact
     * regions constrain harder, which keeps the repair loop short.
     */
    private fun growRegions(n: Int, q: Int, placement: List<Int>): IntArray? {
        val regions = IntArray(n * n) { -1 }
        val frontier = Array(n) { HashSet<Int>() }
        for (r in 0 until n) {
            for (queenIdx in r * q until (r + 1) * q) {
                val cell = placement[queenIdx]
                regions[cell] = r
                neighbors(cell, n).filterTo(frontier[r]) { regions[it] == -1 }
            }
        }
        val sizes = IntArray(n) { q }
        var unassigned = n * n - n * q
        while (unassigned > 0) {
            val region = smallestWithWork(frontier, sizes) ?: return null // disconnected remnant
            val options = frontier[region].filter { regions[it] == -1 }
            if (options.isEmpty()) {
                frontier[region].clear()
                continue
            }
            val cell = options[random.nextInt(options.size)]
            regions[cell] = region
            frontier[region].remove(cell)
            sizes[region]++
            unassigned--
            for (nb in neighbors(cell, n)) {
                if (regions[nb] == -1) frontier[region].add(nb)
            }
        }
        return regions
    }

    /** Region with the fewest cells that still has an assignable frontier cell. */
    private fun smallestWithWork(frontier: Array<HashSet<Int>>, sizes: IntArray): Int? {
        var best = -1
        for (r in frontier.indices) {
            if (frontier[r].isEmpty()) continue
            if (best == -1 || sizes[r] < sizes[best]) best = r
        }
        return best.takeIf { it >= 0 }
    }

    /** 4-adjacency BFS distance table from [seed] over the whole board. */
    private fun bfsDistances(seed: Int, n: Int): IntArray {
        val dist = IntArray(n * n) { -1 }
        dist[seed] = 0
        val queue = ArrayDeque<Int>()
        queue.add(seed)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (nb in neighbors(cell, n)) {
                if (dist[nb] == -1) {
                    dist[nb] = dist[cell] + 1
                    queue.add(nb)
                }
            }
        }
        return dist
    }

    private fun neighbors(cell: Int, n: Int): List<Int> {
        val r = cell / n
        val c = cell % n
        val out = ArrayList<Int>(4)
        if (r > 0) out += cell - n
        if (r < n - 1) out += cell + n
        if (c > 0) out += cell - 1
        if (c < n - 1) out += cell + 1
        return out
    }

    private companion object {
        /** Repair budget per attempt (measured: n10 converges around ~200 steps). */
        const val MAX_REPAIR_STEPS = 800
    }
}
