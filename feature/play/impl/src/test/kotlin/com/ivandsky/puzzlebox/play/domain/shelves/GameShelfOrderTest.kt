package com.ivandsky.puzzlebox.play.domain.shelves

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.miniSudoku
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.queens
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.strands
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.tango
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.zip
import org.junit.Assert.assertEquals
import org.junit.Test

class GameShelfOrderTest {

    @Test
    fun `R5 no play history gives Queens, Zip, Tango, Mini Sudoku, Strands`() {
        assertEquals(listOf(queens, zip, tango, miniSudoku, strands), gameShelfOrder(mostPlayedFirst = emptyList()))
    }

    @Test
    fun `R5 the most played game goes first and the rest keep the default order`() {
        assertEquals(listOf(tango, queens, zip, miniSudoku, strands), gameShelfOrder(listOf(tango)))
        assertEquals(listOf(strands, tango, queens, zip, miniSudoku), gameShelfOrder(listOf(strands, tango)))
    }

    @Test
    fun `R5 a repeated or unknown game in the history changes nothing else`() {
        assertEquals(
            listOf(zip, queens, tango, miniSudoku, strands),
            gameShelfOrder(listOf(zip, zip, GameId("gone"))),
        )
    }

    @Test
    fun `R5 a game list from the registry replaces the default`() {
        val sudoku = GameId("sudoku")

        assertEquals(listOf(sudoku, zip, queens), gameShelfOrder(listOf(sudoku), games = listOf(zip, queens, sudoku)))
    }
}
