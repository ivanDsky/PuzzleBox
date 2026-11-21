package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.LocalAccentPalette
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapCardColors
import com.ivandsky.puzzlebox.designsystem.monthPalette
import com.ivandsky.puzzlebox.designsystem.recapCardColors
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/** The colors a recap card paints with in the [dark] or light theme: a month card its own month's, every other card its palette entry's. */
internal fun RecapCard.colors(dark: Boolean): RecapCardColors = when (this) {
    is RecapCard.Solved -> recapCardColors(PaletteEntry.Solved, dark)
    is RecapCard.Fastest -> recapCardColors(PaletteEntry.Fastest, dark)
    is RecapCard.Streak -> recapCardColors(PaletteEntry.Streak, dark)
    is RecapCard.Moments -> recapCardColors(PaletteEntry.Moments, dark)
    is RecapCard.Friends -> recapCardColors(PaletteEntry.Friends, dark)
    is RecapCard.TotalTime -> recapCardColors(PaletteEntry.TotalTime, dark)
    is RecapCard.Month -> monthPalette(month.month, dark)
    is RecapCard.YearFinale -> recapCardColors(PaletteEntry.YearFinale, dark)
}

/**
 * One story page's [colors]: the field the deck fills the card and the story with, the ink on it, and the plate the face
 * sits on. A [flat] page has no plate: its face fills the card edge to edge on the field, with no rim around it.
 */
internal data class RecapPagePalette(val colors: RecapCardColors, val flat: Boolean = false) {
    val field: Color get() = colors.field
    val ink: Color get() = colors.ink
    val plate: Color get() = colors.plate
}

/**
 * The palette of [page] in [cards] (null is the cover) in the [dark] or light theme: its card's colors, the Share page's
 * past the last card, and a Fastest game its own [accent] edge to edge, the accent's on-color as ink.
 */
internal fun pagePalette(cards: List<RecapCard>, page: RecapPage?, dark: Boolean, accent: (GameId) -> GameAccent): RecapPagePalette {
    if (page == null) return RecapPagePalette(recapCardColors(PaletteEntry.Cover, dark))
    val card = cards.getOrNull(page.cardIndex) ?: return RecapPagePalette(recapCardColors(PaletteEntry.Share, dark))
    if (card is RecapCard.Fastest) {
        val game = accent(card.rows[page.sub].game)
        return RecapPagePalette(RecapCardColors(field = game.accent, ink = game.onAccent, plate = game.accent), flat = true)
    }
    return RecapPagePalette(card.colors(dark))
}

/** [page]'s palette in the resolved theme, a Fastest game in its brand's accent. */
@Composable
internal fun pagePalette(cards: List<RecapCard>, page: RecapPage?): RecapPagePalette {
    val brands = LocalGameBrands.current
    val accents = LocalAccentPalette.current
    return pagePalette(cards, page, LocalDarkTheme.current) { accents.getValue(brands(it).accent) }
}
