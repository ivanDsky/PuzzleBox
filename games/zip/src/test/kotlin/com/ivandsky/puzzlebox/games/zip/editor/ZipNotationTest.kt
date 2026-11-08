package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zip notation v1 (M5-11-a): parse/print round trips, the four canonical fixtures and one
 * error per grammar rule, each pinned to its whole-text 1-based line and column. The
 * whole-text blanking (header + `#` lines → "") is simulated here the same way
 * [com.ivandsky.puzzlebox.game.editor.NotationText] does it, exactly what the real caller passes.
 */
class ZipNotationTest {

    private fun blanked(vararg lines: String): String = lines.joinToString("\n") {
        if (it.startsWith("puzzlebox ") || it.startsWith("#")) "" else it
    } + "\n"

    private fun blanked(text: String): String =
        blanked(*text.lines().dropLastWhile { it.isEmpty() }.toTypedArray())

    private fun parse(text: String): NotationResult<ZipDraft> = ZipNotation.parse(text)

    private fun err(text: String): NotationResult.Err =
        parse(text) as NotationResult.Err

    /** A full boustrophedon path over an n×n board, (1,1) → (n,1). */
    private fun snake(n: Int): List<Int> = buildList {
        for (r in 0 until n) {
            for (c in 0 until n) add(r * n + if (r % 2 == 0) c else n - 1 - c)
        }
    }

    // --- parse(print(d)) == Ok(d) -----------------------------------------------------------

    @Test
    fun `round trip blank draft`() {
        val d = ZipDraft(6, emptySet(), emptyList(), emptyList())
        assertEquals(NotationResult.Ok(d), parse(blanked(ZipNotation.print(d))))
    }

    @Test
    fun `round trip fully set draft built through editor commands`() {
        val path = snake(5)
        val walls = setOf(
            Wall(Cell(0), WallDirection.DOWN),
            Wall(Cell(7), WallDirection.DOWN),
            Wall(Cell(12), WallDirection.DOWN),
        )
        val d = ZipDraft(5, walls, path, listOf(path.first(), path[12], path[15], path.last()))
        assertEquals(NotationResult.Ok(d), parse(blanked(ZipNotation.print(d))))
    }

    @Test
    fun `round trip partly set draft built through editor commands`() {
        var d = ZipEditor.newDraft(com.ivandsky.puzzlebox.game.VariantId("zip/n6"))
        d = d.stroke(listOf(0, 1, 2, 8, 9)).apply(d)
        d = ZipDraftCommand.ToggleWall(Wall(Cell(3), WallDirection.DOWN)).apply(d)
        d = ZipDraftCommand.ToggleMark(8, d.waypoints, listOf(0, 8, 9)).apply(d)
        assertEquals(NotationResult.Ok(d), parse(blanked(ZipNotation.print(d))))
    }

    // --- fixtures ---------------------------------------------------------------------------

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/notation/$name")!!.readBytes().decodeToString()

    @Test
    fun `fixtures parse, print canonically and match their expect line`() {
        for (name in listOf(
            "zip-unique-6.txt",
            "zip-unique-6-path.txt",
            "zip-multiple-5.txt",
            "zip-incomplete-6.txt",
        )) {
            val text = fixture(name)
            val expect = text.lineSequence().first { it.startsWith("# expect:") }
                .removePrefix("# expect:").trim().split(" ")
            val wantedVerdict = expect.first()
            val wantedBlocks = expect.drop(1)

            val parsed = parse(blanked(*text.lines().toTypedArray()))
            assertTrue("$name should parse", parsed is NotationResult.Ok)
            val d = (parsed as NotationResult.Ok).draft

            assertEquals(
                "$name print not canonical",
                text.trimEnd('\n').lines().filterNot { it.startsWith("#") }.joinToString("\n") + "\n",
                ZipNotation.print(d),
            )

            val verdict = ZipEditor.analyze(d, Budget.Unlimited)
            assertEquals("$name verdict", wantedVerdict, verdict::class.simpleName!!.lowercase())
            val blockIds = ZipEditor.publishBlocks(d, verdict).map { it.id }
            assertEquals("$name blocks", wantedBlocks, blockIds)
        }
    }

    @Test
    fun `notation exposes version and columns`() {
        assertEquals(1, ZipNotation.notationVersion)
        assertEquals(6, ZipNotation.columns(ZipDraft(6, emptySet(), emptyList(), emptyList())))
    }

    // --- errors: one per grammar rule -------------------------------------------------------

    private fun board(vararg rows: String, extra: List<String> = emptyList()): String {
        val lines = rows.toList() + extra
        return blanked("puzzlebox zip v1", *lines.toTypedArray())
    }

    @Test
    fun `no rows errors at 1,1`() {
        assertEquals(NotationResult.Err(1, 1, "no rows after the header"), err(board()))
    }

    @Test
    fun `size outside the variant table errors`() {
        assertEquals(
            NotationResult.Err(2, 1, "board is 4 rows; expected 5 to 8"),
            err(blanked("puzzlebox zip v1", "1 . . .", ". . . .", ". . 2 .", ". . . .")),
        )
    }

    @Test
    fun `row with too few tokens errors past its last token`() {
        assertEquals(
            NotationResult.Err(4, 10, "row 3 has 5 cells, expected 6"),
            err(board("1 . . . . .", ". . . . . .", "2 . . . .", ". . . . . .", ". . . . . .", ". . . . . .")),
        )
    }

    @Test
    fun `row with too many tokens errors at the extra token`() {
        assertEquals(
            NotationResult.Err(2, 13, "row 1 has 7 cells, expected 6"),
            err(board("1 . . . . . .", ". . . . . .", ". . . . . .", ". . . . . .", ". . . . . .", ". . . . . .")),
        )
    }

    @Test
    fun `unexpected token errors at its column`() {
        assertEquals(
            NotationResult.Err(4, 3, "unexpected token 'x'"),
            err(board("1 . . . . .", ". . . . . .", ". x . . . .", ". . . . . .", ". . . . . .", ". . . . . .")),
        )
    }

    @Test
    fun `zero label errors`() {
        assertEquals(
            NotationResult.Err(2, 1, "unexpected token '0'"),
            err(board("0 . . . . .", ". . . . . .", ". . . . . .", ". . . . . .", ". . . . . .", ". . . . . .")),
        )
    }

    @Test
    fun `duplicate label errors at the second one`() {
        assertEquals(
            NotationResult.Err(5, 3, "duplicate label 2"),
            err(board("1 . . . . .", ". . . . . .", "2 . . . . .", ". 2 . . . .", ". . . . . .", ". . . . . .")),
        )
    }

    @Test
    fun `label gap errors at the too-large label`() {
        assertEquals(
            NotationResult.Err(7, 3, "label 5 outside 1..4"),
            err(board("1 . . . . .", ". . . . . .", "2 . . . . .", ". . . . . .", "3 . . . . .", ". 5 . . . .")),
        )
    }

    @Test
    fun `wall on the outer border errors at its cell token`() {
        assertEquals(
            NotationResult.Err(8, 6, "wall on the outer border"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("wall 1,6 right"),
                ),
            ),
        )
    }

    @Test
    fun `duplicate wall errors at its cell token`() {
        assertEquals(
            NotationResult.Err(9, 6, "duplicate wall"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("wall 2,3 right", "wall 2,3 right"),
                ),
            ),
        )
    }

    @Test
    fun `wall cell outside the board errors at its cell token`() {
        assertEquals(
            NotationResult.Err(8, 6, "cell 7,1 outside the board"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("wall 7,1 down"),
                ),
            ),
        )
    }

    @Test
    fun `unknown wall direction errors at its token`() {
        assertEquals(
            NotationResult.Err(8, 10, "unexpected token 'up'"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("wall 1,1 up"),
                ),
            ),
        )
    }

    @Test
    fun `path repeating a cell errors at the repeat`() {
        assertEquals(
            NotationResult.Err(8, 14, "path visits cell 1,2 twice"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("path 1,1 1,2 1,2"),
                ),
            ),
        )
    }

    @Test
    fun `non-orthogonal path step errors at the later cell`() {
        assertEquals(
            NotationResult.Err(8, 10, "path step is not orthogonal"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("path 1,1 2,2"),
                ),
            ),
        )
    }

    @Test
    fun `wall between path cells errors at the later cell`() {
        assertEquals(
            NotationResult.Err(9, 10, "a wall blocks the path"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("wall 1,1 right", "path 1,1 1,2"),
                ),
            ),
        )
    }

    @Test
    fun `board row after a command errors at its line`() {
        assertEquals(
            NotationResult.Err(9, 1, "rows must come before wall and path lines"),
            err(
                blanked(
                    "puzzlebox zip v1",
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    "path 1,1 1,2",
                    "1 . . . . .",
                ),
            ),
        )
    }

    @Test
    fun `labels off the path error at the path line`() {
        assertEquals(
            NotationResult.Err(8, 1, "labels disagree with the path"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("path 1,2 1,3"),
                ),
            ),
        )
    }

    @Test
    fun `path cell outside the board errors at its token`() {
        assertEquals(
            NotationResult.Err(8, 10, "cell 7,1 outside the board"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("path 1,1 7,1"),
                ),
            ),
        )
    }

    @Test
    fun `wall after the path line blocking it errors at the wall token`() {
        assertEquals(
            NotationResult.Err(9, 6, "a wall blocks the path"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("path 1,1 1,2", "wall 1,1 right"),
                ),
            ),
        )
    }

    @Test
    fun `path with no labels errors at the path line`() {
        assertEquals(
            NotationResult.Err(8, 1, "labels disagree with the path"),
            err(
                board(
                    ". . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . .",
                    extra = listOf("path 1,1 1,2"),
                ),
            ),
        )
    }

    @Test
    fun `signed label errors`() {
        assertEquals(
            NotationResult.Err(2, 1, "unexpected token '+5'"),
            err(board("+5 . . . . .", ". . . . . .", ". . . . . .", ". . . . . .", ". . . . . .", ". . . . . .")),
        )
    }

    @Test
    fun `malformed wall cell token errors at its column`() {
        assertEquals(
            NotationResult.Err(8, 6, "unexpected token '2;3'"),
            err(
                board(
                    "1 . . . . .", ". . . . . .", ". . . . . .",
                    ". . . . . .", ". . . . . .", ". . . . . 2",
                    extra = listOf("wall 2;3 right"),
                ),
            ),
        )
    }
}
