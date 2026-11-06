package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication

class SudokuBrandTest {

    private val koin = koinApplication { modules(miniSudokuUiModule, sudokuUiModule) }.koin
    private val mini: GamePlayUi = koin.get(qualifier(SudokuGames.MINI.id.value))
    private val classic: GamePlayUi = koin.get(qualifier(SudokuGames.CLASSIC.id.value))
    private val registry = GamePlayRegistry(listOf(mini, classic))

    @Test
    fun `mini registers teal with its own glyph`() {
        val brand = registry.brandOf(SudokuGames.MINI.id)
        assertEquals(AccentSlot.Teal, brand.accent)
        assertSame(MiniSudokuGlyph, brand.glyph)
        assertNotEquals(GameBrand.Neutral.glyph, brand.glyph)
    }

    @Test
    fun `classic stays neutral`() {
        assertEquals(GameBrand.Neutral, registry.brandOf(SudokuGames.CLASSIC.id))
    }

    @Test
    fun `hubLine is the variant label, same for every board state`() {
        val fresh = BoardPreview.InProgress(ByteArray(0))
        val line = mini.hubLine(fresh)
        assertNotNull(line)
        assertTrue(line!!.contains(SudokuGames.MINI.variant.geometry.size.toString()))
        assertNotEquals(line, classic.hubLine(fresh))
    }
}
