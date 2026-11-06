package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MiniSudokuWireParserTest {

    private val date = LocalDate.of(2026, 11, 4)
    private val json = MiniSudokuGameExtractor.extract(KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json"))!!
        .miniSudokuGamePuzzleJson

    private fun parse(text: String = json) = MiniSudokuWireParser.parse(text, date, "450", SudokuVariants.MINI)

    private fun assertMalformed(text: String) {
        val result = parse(text)
        assertTrue("$result", result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }

    @Test
    fun `#450 parses to its 12 givens and the published solution`() {
        val daily = (parse() as DecodeResult.Ok).value
        assertEquals(KnownSolutions.peanut450, daily.puzzle)
        assertEquals(KnownSolutions.PEANUT_450_PRESETS, daily.puzzle.givens.indices.filter { daily.puzzle.givens[it] != 0 })
        assertEquals(SudokuPuzzleRef(SudokuGames.MINI.id, date, 450), daily.ref)
    }

    @Test
    fun `a missing or non-numeric edition leaves the number null`() {
        val daily = (MiniSudokuWireParser.parse(json, date, null, SudokuVariants.MINI) as DecodeResult.Ok).value
        assertEquals(null, daily.ref.puzzleNumber)
    }

    @Test
    fun `a 9x9 payload under the mini variant is a size mismatch`() {
        val nine = """{"gridRowSize":9,"gridColSize":9,"regions":[],"presetCellIdxes":[],"solution":[]}"""
        assertEquals(DecodeResult.Err(DecodeError.VariantSizeMismatch("sudoku/6x6", 6, 9)), parse(nine))
    }

    @Test
    fun `a non-square grid is malformed`() {
        assertMalformed(json.replace("\"gridColSize\":6", "\"gridColSize\":5"))
    }

    @Test
    fun `regions that are not the boxes are malformed`() {
        assertMalformed(json.replace("[0,1,2,6,7,8]", "[0,1,2,3,4,5]").replace("[3,4,5,9,10,11]", "[6,7,8,9,10,11]"))
        assertMalformed(json.replace(",\n        {\"cellIdxes\":[27,28,29,33,34,35]}", ""))
    }

    @Test
    fun `duplicate or out-of-range presets are malformed`() {
        assertMalformed(json.replace("[2,4,7,9,", "[2,2,7,9,"))
        assertMalformed(json.replace("[2,4,7,9,", "[36,4,7,9,"))
    }

    @Test
    fun `a solution that repeats a digit in a unit is malformed`() {
        assertMalformed(json.replace("[4,6,3,2,5,1,", "[6,4,3,2,5,1,"))
        assertMalformed(json.replace("[4,6,3,2,5,1,", "[4,6,3,2,5,7,"))
        assertMalformed(json.replace("[4,6,3,2,5,1,", "[4,6,3,2,5,"))
    }

    @Test
    fun `column restrictions are refused unless empty or absent`() {
        assertMalformed(json.replace("\"columnRestrictions\":[]", "\"columnRestrictions\":[{\"col\":0}]"))
        assertMalformed(json.replace("\"columnRestrictions\":[]", "\"columnRestrictions\":\"none\""))
        assertTrue(parse(json.replace("\"columnRestrictions\":[],", "")) is DecodeResult.Ok)
    }

    @Test
    fun `garbage is malformed, never a throw`() {
        assertMalformed("not json")
        assertMalformed("[]")
        assertMalformed("{}")
        assertMalformed(json.replace("\"gridRowSize\":6", "\"gridRowSize\":\"6\""))
    }
}
