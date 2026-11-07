package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication

class SudokuBrandTest {

    private val koin = koinApplication { modules(miniSudokuUiModule, sudokuUiModule) }.koin
    private val mini: GamePlayUi = koin.get(qualifier(SudokuGames.MINI.id.value))
    private val classic: GamePlayUi = koin.get(qualifier(SudokuGames.CLASSIC.id.value))
    private val registry = GamePlayRegistry(listOf(mini, classic))

    @Test
    fun `mini registers teal with the sudoku glyph`() {
        val brand = registry.brandOf(SudokuGames.MINI.id)
        assertEquals(AccentSlot.Teal, brand.accent)
        assertSame(GameGlyphs.Sudoku, brand.glyph)
        assertNotEquals(GameBrand.Neutral.glyph, brand.glyph)
    }

    @Test
    fun `classic keeps the neutral hue and shares the sudoku glyph`() {
        val brand = registry.brandOf(SudokuGames.CLASSIC.id)
        assertEquals(AccentSlot.Neutral, brand.accent)
        assertSame(GameGlyphs.Sudoku, brand.glyph)
    }
}
