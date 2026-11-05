package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniSudokuGameExtractorTest {

    private val shapes = listOf(
        "linkedin-mini-sudoku-graphql-450.json",
        "linkedin-mini-sudoku-normalized-450.json",
        "linkedin-mini-sudoku-value-450.json",
    )

    @Test
    fun `all three response shapes yield the puzzle object and edition 450`() {
        for (name in shapes) {
            val match = MiniSudokuGameExtractor.extract(KnownSolutions.fixture(name))
            assertNotNull(name, match)
            assertEquals(name, "450", match!!.editionText)
            assertTrue(name, match.miniSudokuGamePuzzleJson.startsWith("{"))
            assertTrue(name, match.miniSudokuGamePuzzleJson.contains("\"presetCellIdxes\""))
        }
    }

    @Test
    fun `a type-map mention or a null union is not a puzzle`() {
        assertNull(MiniSudokuGameExtractor.extract(KnownSolutions.fixture("linkedin-mini-sudoku-metadata-only.json")))
        assertNull(MiniSudokuGameExtractor.extract("""{"miniSudokuGamePuzzle":{"name":"no grid"}}"""))
        assertNull(MiniSudokuGameExtractor.extract(""))
    }

    @Test
    fun `the edition falls back to puzzleId without an entityUrn`() {
        val body = KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json")
            .replace(Regex("\"entityUrn\":\"[^\"]*\","), "")
        assertEquals("450", MiniSudokuGameExtractor.extract(body)!!.editionText)
    }

    @Test
    fun `braces inside strings do not end the object`() {
        val body = """{"miniSudokuGamePuzzle":{"name":"a}b{\"c","gridRowSize":6,"gridColSize":6}}"""
        assertEquals(
            """{"name":"a}b{\"c","gridRowSize":6,"gridColSize":6}""",
            MiniSudokuGameExtractor.extract(body)!!.miniSudokuGamePuzzleJson,
        )
    }
}
