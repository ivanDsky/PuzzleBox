package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariant
import kotlin.random.Random

/**
 * Random uniquely-solvable boards for property tests (F2 §6): a Warnsdorff-style random
 * Hamiltonian path, walls dropped consistently with it, waypoints placed along the path,
 * then the solver filter — only boards with EXACTLY ONE solution survive. Deterministic
 * for a given [Random] seed, so failures reproduce.
 *
 * Test-only (F2 §6): production puzzles come from the daily fetch; random generation is a
 * property-test instrument, never shipped code.
 */
class ZipGenerator(private val random: Random = Random.Default) {

    private val solver = ZipSolver()

    /** One generation attempt; null when the random board did not come out unique. */
    fun next(
        variant: ZipVariant,
        wallProbability: Double = 0.7,
        waypointCount: Int = 3 + random.nextInt(2),
    ): ZipPuzzle? {
        require(variant.size in 5..8) { "generator tuned for daily sizes, got ${variant.size}" }
        require(waypointCount >= 2) { "need start + end waypoints at minimum" }
        val n = variant.size
        val path = randomHamiltonianPath(n) ?: return null

        // Walls: interior edges NOT traversed by the path, included independently.
        val pathEdges = HashSet<Long>(path.size * 2)
        for (i in 0 until path.lastIndex) {
            pathEdges += edgeKey(path[i].idx, path[i + 1].idx, n)
        }
        val walls = HashSet<Wall>()
        for (idx in 0 until n * n) {
            val r = idx / n
            val c = idx % n
            if (c < n - 1 && edgeKey(idx, idx + 1, n) !in pathEdges &&
                random.nextDouble() < wallProbability
            ) {
                walls += Wall(Cell(idx), WallDirection.RIGHT)
            }
            if (r < n - 1 && edgeKey(idx, idx + n, n) !in pathEdges &&
                random.nextDouble() < wallProbability
            ) {
                walls += Wall(Cell(idx), WallDirection.DOWN)
            }
        }

        // Waypoints: start and end of the solution always labeled (I4/I6), k-2 random
        // distinct interior positions in between, ascending along the path.
        val interior = (1 until n * n - 1).toMutableList().also { interior ->
            for (i in interior.size - 1 downTo 1) {
                val j = random.nextInt(i + 1)
                interior[i] = interior[j].also { interior[j] = interior[i] }
            }
        }
        val indices = (sortedSetOf(0, n * n - 1) + interior.take((waypointCount - 2).coerceAtLeast(0)))
            .sorted()
        val waypoints = indices.map { path[it] }

        val puzzle = ZipPuzzle(
            variant = variant.id,
            board = ZipBoard(n = n, walls = walls, waypoints = waypoints),
            solution = path,
        )
        return if (solver.countSolutions(puzzle, limit = 2) == 1) puzzle else null
    }

    /** Retries until a unique board appears or [maxAttempts] run out. */
    fun nextUnique(variant: ZipVariant, maxAttempts: Int = 200): ZipPuzzle? {
        repeat(maxAttempts) {
            next(variant)?.let { return it }
        }
        return null
    }

    /** Warnsdorff-style random Hamiltonian path with backtracking, or null on exhaustion. */
    private fun randomHamiltonianPath(n: Int): List<Cell>? {
        val total = n * n
        repeat(RESTARTS) {
            val board = ZipBoard(n, emptySet(), emptyList())
            val visited = BooleanArray(total)
            val path = ArrayList<Cell>(total)
            val start = Cell(random.nextInt(total))
            visited[start.idx] = true
            path.add(start)
            if (walk(board, n, visited, path)) return path
        }
        return null
    }

    private fun walk(board: ZipBoard, n: Int, visited: BooleanArray, path: MutableList<Cell>): Boolean {
        if (path.size == board.cellCount) return true
        val end = path.last()
        val candidates = board.neighbors(end)
            .filter { !visited[it.idx] }
            .map { it to board.neighbors(it).count { next -> !visited[next.idx] } }
            .sortedWith(compareBy({ it.second }, { random.nextInt() })) // Warnsdorff + random tie-break
        for ((candidate, _) in candidates) {
            visited[candidate.idx] = true
            path.add(candidate)
            if (walk(board, n, visited, path)) return true
            path.removeAt(path.lastIndex)
            visited[candidate.idx] = false
        }
        return false
    }

    /** Interior edge as a stable (cellIdx, dirOrdinal) pair packed into a long. */
    private fun edgeKey(a: Int, b: Int, n: Int): Long = when {
        b == a + 1 -> (a.toLong() shl 8) // RIGHT wall at a
        a == b + 1 -> (b.toLong() shl 8) // RIGHT wall at b
        b == a + n -> (a.toLong() shl 8) or 1L // DOWN wall at a
        a == b + n -> (b.toLong() shl 8) or 1L // DOWN wall at b
        else -> throw IllegalArgumentException("not an interior edge: $a -> $b (n=$n)")
    }

    private companion object {
        const val RESTARTS = 8
    }
}
