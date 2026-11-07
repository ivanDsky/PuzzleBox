package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.DarkBoardTokens
import com.ivandsky.puzzlebox.designsystem.DarkColorScheme
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.LightColorScheme
import com.ivandsky.puzzlebox.games.queensui.QueensBoardDefaults
import com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor diagnostics' board tokens (23, 40; spec S19) against every Queens region fill, in
 * light and dark. They are non-text marks, so each clears WCAG 1.4.11's 3:1 over the fill it
 * lands on: the diff ring, the split stripes, the ghost crown's dashed outline (the ring tint
 * at the editor's ghost alpha) and the clash wash (composited over the fill). A crown or ×
 * on a clash cell is drawn over the wash, so its ink clears the 4.5:1 glyph floor against the
 * washed fill. The region fills are Queens' own palette, so the guard lives here rather than
 * in core:designsystem's BoardTokensTest, which cannot see them.
 */
class QueensEditorTokenContrastTest {

    @Test
    fun `the diff ring clears 3 to 1 on every region fill`() = everyFill { tokens, _, fill ->
        tokens.editorDiffRing to fill
    }

    @Test
    fun `the split stripes clear 3 to 1 on every region fill`() = everyFill { tokens, _, fill ->
        tokens.editorSplitHatch to fill
    }

    @Test
    fun `the ghost outline clears 3 to 1 on every region fill`() = everyFill { tokens, _, fill ->
        tokens.editorDiffRing.copy(alpha = QueensEditorDefaults.ghostOutlineAlpha).compositeOver(fill) to fill
    }

    @Test
    fun `the clash wash clears 3 to 1 on every region fill`() = everyFill { tokens, _, fill ->
        tokens.editorClashFill.compositeOver(fill) to fill
    }

    @Test
    fun `a crown on a clash cell clears 4 point 5 to 1 on the wash`() = everyFill(bar = GLYPH_BAR) { tokens, scheme, fill ->
        crownInk(fill, tokens.editorClashFill, scheme.onSurface, scheme.scrim, scheme.surface) to tokens.editorClashFill.compositeOver(fill)
    }

    @Test
    fun `the editor ghost is fainter than a placed crown but keeps its own alpha off the play ghost`() {
        assertTrue(QueensEditorDefaults.ghostOutlineAlpha < 1f)
        assertTrue(QueensEditorDefaults.ghostOutlineAlpha > QueensBoardDefaults.ghostOutlineAlpha)
    }

    /** Asserts [pair]'s (mark, ground) contrast ≥ [bar] for every palette region, both themes. */
    private fun everyFill(bar: Float = MARK_BAR, pair: (BoardColors, ColorScheme, Color) -> Pair<Color, Color>) {
        for ((dark, tokens, scheme) in THEMES) {
            for (region in 0 until PALETTE_SIZE) {
                val fill = QueensRegionPalette.fill(region, dark)
                val (mark, ground) = pair(tokens, scheme, fill)
                val ratio = QueensRegionPalette.contrast(mark, ground)
                assertTrue(
                    "${if (dark) "dark" else "light"} region $region: $ratio < $bar",
                    ratio >= bar,
                )
            }
        }
    }

    private companion object {
        const val MARK_BAR = 3f
        const val GLYPH_BAR = 4.5f

        /** The palette's hue count; ids past it repeat a fill (with the dot texture on top). */
        const val PALETTE_SIZE = 10

        val THEMES = listOf(
            Triple(false, LightBoardTokens, LightColorScheme),
            Triple(true, DarkBoardTokens, DarkColorScheme),
        )
    }
}
