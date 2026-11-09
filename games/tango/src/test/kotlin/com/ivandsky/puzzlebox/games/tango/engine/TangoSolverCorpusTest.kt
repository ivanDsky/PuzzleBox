package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.editor.TangoClashLocator
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraftCodec
import com.ivandsky.puzzlebox.games.tango.editor.TangoNotation
import com.ivandsky.puzzlebox.games.tango.editor.TangoShareCodec
import com.ivandsky.puzzlebox.games.tango.feed.TangoGameExtractor
import java.io.File
import java.time.LocalDate
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The solver's answers over a fixed corpus: every bundled fixture and daily, the tutorial
 * boards, marker-only traps, odd sizes and 20 generated boards per size 6/8/10 (minimal unique
 * plus one given removed). `solver/corpus-expected.txt` was recorded from the plain
 * backtracking solver before propagation, one `<id> <count up to 2> <solution or ->` per board.
 * A mismatch writes what the solver now answers to `build/tango-solver-corpus-actual.txt`.
 */
class TangoSolverCorpusTest {

    @Test
    fun `countSolutions and solve keep the recorded answer for every corpus board`() {
        val actual = corpus.joinToString(separator = "") { (id, puzzle) -> "$id ${answer(puzzle)}\n" }
        val expected = javaClass.getResource(EXPECTED)?.readText().orEmpty()
        if (actual != expected) File("build/tango-solver-corpus-actual.txt").writeText(actual)

        assertEquals(expected, actual)
    }

    /**
     * Below the limit the set is every solution, so it is the same set as before; at the limit
     * the search order picks which ones, so only size, distinctness and validity are pinned.
     */
    @Test
    fun `bounded returns the same solution set as countSolutions and solve`() {
        for ((id, puzzle) in corpus.filter { (_, puzzle) -> TangoClashLocator.conflicts(puzzle).isEmpty() }) {
            val outcome = TangoUniqueness.bounded(puzzle, limit = 2, budget = Budget.Unlimited)

            val solutions = (outcome as TangoSearchOutcome.Done).solutions
            assertEquals(id, TangoUniqueness.countSolutions(puzzle, limit = 2), solutions.size)
            assertEquals(id, solutions.size, solutions.toSet().size)
            solutions.forEach { solution ->
                assertTrue(id, puzzle.givens.all { (idx, cell) -> solution[idx] == cell })
                assertTrue(id, TangoRules.isComplete(TangoState(puzzle, solution)))
            }
            if (solutions.size == 1) assertEquals(id, solutions.single(), TangoUniqueness.solve(puzzle))
        }
    }

    @Test
    fun `an odd size is rejected up front even when its givens fill the board`() {
        val full = TangoPuzzle(VariantId("tango/n3"), 3, oddFull, emptyList())

        assertFalse(TangoUniqueness.isUniquelySolvable(full))
        assertNull(TangoUniqueness.solve(full))
    }

    private fun answer(puzzle: TangoPuzzle): String {
        val solution = TangoUniqueness.solve(puzzle)?.joinToString("") { if (it == TangoCell.SUN) "S" else "M" }
        return "${TangoUniqueness.countSolutions(puzzle, limit = 2)} ${solution ?: "-"}"
    }

    private companion object {
        const val EXPECTED = "/solver/corpus-expected.txt"
        val day: LocalDate = LocalDate.of(2026, 11, 5)
        val oddFull = "SMSMSMSMS".withIndex().associate { (idx, ch) -> idx to cell(ch) }

        val corpus: List<Pair<String, TangoPuzzle>> by lazy {
            buildList {
                for (name in listOf("01-tango-stream-today.txt", "linkedin-tango-byid-1.json", "linkedin-tango-byid-301.json", "linkedin-tango-byid-758.json")) {
                    val match = requireNotNull(TangoGameExtractor.extract(String(bytes("/fixtures/$name")))) { name }
                    add(name to ok(LotkaWireParser.parse(match.lotkaGamePuzzleJson, day, match.editionText)).puzzle)
                }
                add("tango-sample-n6.bin" to ok(TangoCodec.decode(bytes("/fixtures/tango-sample-n6.bin"))))
                for (name in listOf("tango-draft-v1.bin", "tango-draft-v1-commands.bin")) {
                    add(name to puzzleOf(ok(TangoDraftCodec.decode(bytes("/fixtures/$name"))).draft))
                }
                for (name in listOf("tango-session-v2.bin", "tango-session-v3-custom.bin")) {
                    add(name to ok(TangoSessionCodec.deserialize(bytes("/fixtures/$name"))).puzzle)
                }
                add("tango-share-v1.bin" to puzzleOf(ok(TangoShareCodec.decodeShare(bytes("/editor/tango-share-v1.bin")))))
                for (name in listOf("tango-unique.txt", "tango-multiple.txt", "tango-none.txt", "tango-needs-guessing.txt")) {
                    val text = String(bytes("/notation/$name")).lines().filterNot { it.startsWith("puzzlebox ") }.joinToString("\n")
                    val parsed = TangoNotation.parse(text)
                    add(name to puzzleOf((parsed as NotationResult.Ok).draft))
                }
                add("test-support-sample" to TangoTestSupport.sample)
                tutorial.forEachIndexed { step, puzzle -> add("tutorial-$step" to puzzle) }
                add("n10-hard-seed26.bin" to ok(TangoCodec.decode(bytes("/solver/n10-hard-seed26.bin"))))
                add("parity-n4" to TangoBoardGenerator.parityTrap(4))
                add("parity-n6" to TangoBoardGenerator.parityTrap(6))
                add("odd-n3-full" to TangoPuzzle(VariantId("tango/n3"), 3, oddFull, emptyList()))
                addAll(clashBoards)
                add("odd-n5-empty" to TangoPuzzle(VariantId("tango/n5"), 5, emptyMap(), emptyList()))
                for (n in listOf(6, 8, 10)) {
                    for (seed in TangoBoardGenerator.CORPUS_SEEDS) {
                        val boards = TangoBoardGenerator.boards(n, seed)
                        add(generatedId(n, seed, "min", boards.minimal) to boards.minimal)
                        add(generatedId(n, seed, "mult", boards.multiple) to boards.multiple)
                    }
                }
            }
        }

        /** The tutorial lessons (games:tango-ui TangoTutorial), copied here: `S` sun, `M` moon, `.` open. */
        val tutorial: List<TangoPuzzle> = listOf(
            board("SSMM", ".M.S", ".MSM", "MSMS"),
            board("SSMM", "MM.S", "SMSM", "MSMS"),
            board(
                "S.MM", "MMSS", ".MSM", "MSMS",
                markers = listOf(
                    TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                    TangoMarker(8, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                ),
            ),
            board("SSMM", "MMSS", "SMSM", "MSM."),
        )

        /**
         * Givens that already break a rule with no open cell in the broken constraint: a full board
         * with a run of three, a `×` between two equal givens, a given row with a run of three. The editor never
         * searches these (TangoClashLocator first); they have no solution.
         */
        val clashBoards: List<Pair<String, TangoPuzzle>> = listOf(
            "clash-full-triple" to board("SSSSMM", "SSMSMM", "MMSMSS", "SSMMSM", "MMSSMS", "MMSMSS"),
            "clash-marker-on-givens" to TangoTestSupport.sample.copy(
                markers = TangoTestSupport.sample.markers + TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
            ),
            "clash-row-triple" to board("SSSMMM", "......", "......", "......", "......", "......"),
        )

        /** Seed, size and a CRC of the board's TangoCodec bytes, so a changed generator cannot pass silently. */
        fun generatedId(n: Int, seed: Long, kind: String, puzzle: TangoPuzzle): String {
            val crc = CRC32().apply { update(TangoCodec.encode(puzzle)) }.value
            return "gen-n$n-s$seed-$kind-%08x".format(crc)
        }

        fun board(vararg rows: String, markers: List<TangoMarker> = emptyList()): TangoPuzzle {
            val givens = rows.joinToString("").withIndex().filter { it.value != '.' }.associate { (idx, ch) -> idx to cell(ch) }
            return TangoPuzzle(VariantId("tango/n${rows.size}"), rows.size, givens, markers)
        }

        fun puzzleOf(draft: TangoDraft) = TangoPuzzle(TangoDraft.variantOf(draft.n), draft.n, draft.givens, draft.markers)

        fun cell(ch: Char): TangoCell = if (ch == 'S') TangoCell.SUN else TangoCell.MOON

        fun bytes(path: String): ByteArray = requireNotNull(TangoSolverCorpusTest::class.java.getResourceAsStream(path)) { path }.use { it.readBytes() }

        fun <T> ok(result: DecodeResult<T>): T = (result as DecodeResult.Ok).value
    }
}
