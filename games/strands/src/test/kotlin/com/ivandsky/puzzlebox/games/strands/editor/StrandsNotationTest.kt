package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strands notation v1 (M5-14-a): parse/print round trips, the three canonical fixtures and one
 * error per grammar rule, each pinned to its whole-text 1-based line and column. The whole-text
 * blanking (header + `#` lines → "") is simulated here the same way
 * [com.ivandsky.puzzlebox.game.editor.NotationText] does it, exactly what the real caller passes.
 * [board] texts start with the blanked header: the clue is line 2, the grid lines 3 to 10 and
 * the word lines start at line 11.
 */
class StrandsNotationTest {

    private fun blanked(vararg lines: String): String = lines.joinToString("\n") {
        if (it.startsWith("puzzlebox ") || it.startsWith("#")) "" else it
    } + "\n"

    private fun blanked(text: String): String =
        blanked(*text.lines().dropLastWhile { it.isEmpty() }.toTypedArray())

    private fun parse(text: String): NotationResult<StrandsDraft> = StrandsNotation.parse(text)

    private fun err(text: String): NotationResult.Err = parse(text) as NotationResult.Err

    private fun ok(text: String): StrandsDraft {
        val result = parse(text)
        assertTrue("parse failed: $result", result is NotationResult.Ok)
        return (result as NotationResult.Ok).draft
    }

    private fun path(word: String?, vararg coords: Pair<Int, Int>): StrandsDraftPath =
        StrandsDraftPath(coords.map { (r, c) -> (r - 1) * StrandsDraft.COLS + (c - 1) }, word)

    private val blankRow = "......"

    /** The header, the clue, eight grid rows (blank unless replaced by 1-based row), [words]. */
    private fun board(
        vararg words: String,
        clue: String = "clue: x",
        rows: Map<Int, String> = emptyMap(),
    ): String = blanked(
        "puzzlebox strands v1",
        clue,
        *Array(StrandsDraft.ROWS) { rows[it + 1] ?: blankRow },
        *words,
    )

    // --- parse(print(d)) == Ok(d) -----------------------------------------------------------

    @Test
    fun `round trip blank draft`() {
        val d = StrandsEditor.newDraft(StrandsDefinition.VARIANT)
        assertEquals(NotationResult.Ok(d), parse(blanked(StrandsNotation.print(d))))
    }

    @Test
    fun `round trip fully set draft`() {
        val d = StrandsDraft(
            "......" + "......" + "......" + "......" + "SPOONF" + "AAAAAA" + "S....." + "......",
            clue = "In the kitchen",
            paths = listOf(
                path("SPOONF", 5 to 1, 5 to 2, 5 to 3, 5 to 4, 5 to 5, 5 to 6),
                path("SAAAAAA", 7 to 1, 6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5, 6 to 6),
            ),
            spangram = 1,
        )
        assertEquals(NotationResult.Ok(d), parse(blanked(StrandsNotation.print(d))))
    }

    @Test
    fun `round trip partly set draft built through editor commands`() {
        var d = StrandsEditor.newDraft(StrandsDefinition.VARIANT)
        d = StrandsDraftCommand.SetClue("Kitchen things", "").apply(d)
        d = StrandsDraftCommand.AddPath(path(null, 5 to 1, 5 to 2)).apply(d)
        d = StrandsDraftCommand.AddPath(path(null, 1 to 1, 2 to 2, 3 to 3)).apply(d)
        d = StrandsDraftCommand.SetSpangram(1, StrandsDraft.NO_SPANGRAM).apply(d)
        assertEquals(NotationResult.Ok(d), parse(blanked(StrandsNotation.print(d))))
    }

    @Test
    fun `clue spaces round trip as written`() {
        for (clue in listOf("", " ", " lead", "trail ", "a  b")) {
            val d = StrandsEditor.newDraft(StrandsDefinition.VARIANT).copy(clue = clue)
            assertEquals("clue '$clue'", NotationResult.Ok(d), parse(blanked(StrandsNotation.print(d))))
        }
    }

    @Test
    fun `clue needs no space after the colon`() {
        assertEquals("x", ok(board(clue = "clue:x")).clue)
        assertEquals("", ok(board(clue = "clue:")).clue)
    }

    // --- fixtures ---------------------------------------------------------------------------

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/notation/$name")!!.readBytes().decodeToString()

    @Test
    fun `fixtures parse, print canonically and match their expect line`() {
        for (name in listOf("strands-unique.txt", "strands-multiple.txt", "strands-incomplete.txt")) {
            val text = fixture(name)
            val expect = text.lineSequence().first { it.startsWith("# expect:") }
                .removePrefix("# expect:").trim().split(" ")
            val wantedVerdict = expect.first()
            val wantedBlocks = expect.drop(1)

            val d = ok(blanked(text))

            assertEquals(
                "$name print not canonical",
                text.trimEnd('\n').lines().filterNot { it.startsWith("#") }.joinToString("\n") + "\n",
                StrandsNotation.print(d),
            )

            val verdict = StrandsEditor.analyze(d, Budget.Unlimited)
            assertEquals("$name verdict", wantedVerdict, verdict::class.simpleName!!.lowercase())
            val blockIds = StrandsEditor.publishBlocks(d, verdict).map { it.id }
            assertEquals("$name blocks", wantedBlocks, blockIds)
        }
    }

    @Test
    fun `the incomplete fixture has exactly one unnamed path`() {
        val d = ok(blanked(fixture("strands-incomplete.txt")))
        assertEquals(1, d.paths.count { it.word == null })
        assertEquals(0, d.spangram)
    }

    // --- accepted forms ---------------------------------------------------------------------

    @Test
    fun `lowercase grid letters and words are stored uppercase`() {
        val d = ok(board("word fork 1,1 1,2 1,3 1,4", rows = mapOf(1 to "fork..")))
        assertEquals("FORK..", d.letters.take(StrandsDraft.COLS))
        assertEquals("FORK", d.paths.single().word)
    }

    @Test
    fun `unnamed spangram parses as a spangram`() {
        val d = ok(board("word *? 1,1 1,2"))
        assertEquals(0, d.spangram)
        assertEquals(null, d.paths.single().word)
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val text = listOf(
            "puzzlebox strands v1",
            "# a comment",
            "",
            "clue: x",
            "FORK..",
            "",
            "# another",
            "......",
            "......",
            "......",
            "......",
            "......",
            "......",
            "......",
            "word FORK 1,1 1,2 1,3 1,4",
        ).joinToString("\n")
        assertEquals("x", ok(blanked(text)).clue)
    }

    @Test
    fun `any run of spaces or tabs separates tokens`() {
        val d = ok(board("word\t FORK \t1,1  1,2\t1,3 1,4", rows = mapOf(1 to "FORK..")))
        assertEquals(listOf(0, 1, 2, 3), d.paths.single().cells)
    }

    // --- clue and grid errors ---------------------------------------------------------------

    @Test
    fun `empty text errors at 1,1`() {
        assertEquals(NotationResult.Err(1, 1, "expected a `clue:` line"), err(""))
    }

    @Test
    fun `first line that is not a clue errors at its own column`() {
        val text = blanked("puzzlebox strands v1", "  AAAAAA", blankRow)
        assertEquals(NotationResult.Err(2, 3, "expected a `clue:` line"), err(text))
    }

    @Test
    fun `second clue line errors at its own position`() {
        val text = board("clue: two", clue = "clue: one", rows = mapOf(8 to "......"))
        assertEquals(NotationResult.Err(11, 1, "only one `clue:` line is allowed"), err(text))
    }

    @Test
    fun `clue line among the grid rows errors`() {
        val text = blanked("puzzlebox strands v1", "clue: one", blankRow, "  clue: two")
        assertEquals(NotationResult.Err(4, 3, "only one `clue:` line is allowed"), err(text))
    }

    @Test
    fun `short grid row errors just past its last cell`() {
        assertEquals(
            NotationResult.Err(3, 6, "grid row 1 has 5 cells, expected 6"),
            err(board(rows = mapOf(1 to "AAAAA"))),
        )
    }

    @Test
    fun `long grid row errors at the first extra cell`() {
        assertEquals(
            NotationResult.Err(5, 7, "grid row 3 has 7 cells, expected 6"),
            err(board(rows = mapOf(3 to "AAAAAAA"))),
        )
    }

    @Test
    fun `grid row split by a space errors at the second token`() {
        assertEquals(
            NotationResult.Err(4, 4, "unexpected token 'RK..'"),
            err(board(rows = mapOf(2 to "FO RK.."))),
        )
    }

    @Test
    fun `bad grid character errors at its line and column`() {
        assertEquals(
            NotationResult.Err(6, 3, "unexpected character '1'"),
            err(board(rows = mapOf(4 to "..1..."))),
        )
    }

    @Test
    fun `too few grid rows before a word line errors at the word`() {
        val text = blanked(
            "puzzlebox strands v1", "clue: x", blankRow, blankRow, blankRow, blankRow, blankRow, blankRow,
            blankRow, "word FORK 1,1 1,2 1,3 1,4",
        )
        assertEquals(NotationResult.Err(10, 1, "grid has 7 rows, expected 8"), err(text))
    }

    @Test
    fun `too few grid rows at end of text errors on the next line`() {
        val text = blanked("puzzlebox strands v1", "clue: x", blankRow, "", blankRow)
        assertEquals(NotationResult.Err(6, 1, "grid has 2 rows, expected 8"), err(text))
    }

    @Test
    fun `clue only text errors on the first grid line`() {
        assertEquals(
            NotationResult.Err(3, 1, "grid has 0 rows, expected 8"),
            err(blanked("puzzlebox strands v1", "clue: x")),
        )
    }

    @Test
    fun `ninth grid row errors at its line`() {
        assertEquals(NotationResult.Err(11, 1, "expected a `word` line"), err(board(blankRow)))
    }

    @Test
    fun `line after the grid that is not a word line errors at its token`() {
        assertEquals(NotationResult.Err(11, 3, "expected a `word` line"), err(board("  words FORK")))
    }

    // --- word line errors -------------------------------------------------------------------

    private val fork = mapOf(1 to "FORK..")

    @Test
    fun `word with no cells errors just past the word`() {
        assertEquals(
            NotationResult.Err(11, 10, "list this word's cells as r,c"),
            err(board("word FORK", rows = fork)),
        )
    }

    @Test
    fun `unnamed word with no cells errors just past the word`() {
        assertEquals(
            NotationResult.Err(11, 7, "list this word's cells as r,c"),
            err(board("word ?", rows = fork)),
        )
    }

    @Test
    fun `starred word with no cells errors just past the word`() {
        assertEquals(
            NotationResult.Err(11, 11, "list this word's cells as r,c"),
            err(board("word *FORK", rows = fork)),
        )
    }

    @Test
    fun `word line with nothing after it errors just past the keyword`() {
        assertEquals(
            NotationResult.Err(11, 5, "expected a word or `?`"),
            err(board("word", rows = fork)),
        )
    }

    @Test
    fun `word name that is not letters errors at its column`() {
        assertEquals(
            NotationResult.Err(11, 6, "expected a word or `?`"),
            err(board("word F0RK 1,1 1,2 1,3 1,4", rows = fork)),
        )
    }

    @Test
    fun `lone star errors at its column`() {
        assertEquals(
            NotationResult.Err(11, 6, "expected a word or `?`"),
            err(board("word * 1,1 1,2 1,3 1,4", rows = fork)),
        )
    }

    @Test
    fun `second spangram star errors at its column`() {
        assertEquals(
            NotationResult.Err(12, 6, "only one spangram is allowed"),
            err(board("word *FORK 1,1 1,2 1,3 1,4", "word *KIN 1,4 2,4 2,3", rows = mapOf(1 to "FORK..", 2 to "..NI.."))),
        )
    }

    @Test
    fun `malformed cell token errors at its column`() {
        assertEquals(
            NotationResult.Err(11, 11, "expected a cell as r,c"),
            err(board("word FORK 1:1 1,2", rows = fork)),
        )
        assertEquals(
            NotationResult.Err(11, 11, "expected a cell as r,c"),
            err(board("word FORK 01,1 1,2", rows = fork)),
        )
    }

    @Test
    fun `cell outside the board errors at its column`() {
        assertEquals(
            NotationResult.Err(11, 23, "cell 9,4 is outside the board"),
            err(board("word FORK 1,1 1,2 1,3 9,4", rows = fork)),
        )
        assertEquals(
            NotationResult.Err(11, 19, "cell 1,7 is outside the board"),
            err(board("word FORK 1,1 1,2 1,7 1,4", rows = fork)),
        )
        assertEquals(
            NotationResult.Err(11, 11, "cell 0,1 is outside the board"),
            err(board("word FORK 0,1 1,2 1,3 1,4", rows = fork)),
        )
    }

    @Test
    fun `non neighbouring cells error at the later cell`() {
        assertEquals(
            NotationResult.Err(11, 15, "cells must be 8-neighbour steps"),
            err(board("word FORK 1,1 1,3 1,4", rows = fork)),
        )
    }

    @Test
    fun `repeated cell in one path errors at the repeat`() {
        assertEquals(
            NotationResult.Err(11, 19, "cell 1,1 is already used"),
            err(board("word FORK 1,1 1,2 1,1 1,4", rows = fork)),
        )
    }

    @Test
    fun `path crossing another path errors at the crossing cell`() {
        assertEquals(
            NotationResult.Err(12, 10, "cell 1,4 is already used"),
            err(
                board(
                    "word FORK 1,1 1,2 1,3 1,4",
                    "word KIN 1,4 2,4 2,3",
                    rows = mapOf(1 to "FORK..", 2 to "..NI.."),
                ),
            ),
        )
    }

    @Test
    fun `path that does not spell the word errors at the word`() {
        assertEquals(
            NotationResult.Err(11, 6, "the path spells SPOO, not FORK"),
            err(board("word FORK 1,1 1,2 1,3 1,4", rows = mapOf(1 to "SPOO.."))),
        )
    }

    @Test
    fun `path shorter than its word errors at the word`() {
        assertEquals(
            NotationResult.Err(11, 6, "the path spells FOR, not FORK"),
            err(board("word FORK 1,1 1,2 1,3", rows = fork)),
        )
    }
}
