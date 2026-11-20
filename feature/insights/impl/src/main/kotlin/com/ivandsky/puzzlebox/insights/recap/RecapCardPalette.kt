package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/** The palette entry a recap card paints with; the hint-free count shows inside Solved, so it shares its colours. */
internal fun RecapCard.palette(): PaletteEntry = when (this) {
    is RecapCard.Solved, is RecapCard.HintFree -> PaletteEntry.Solved
    is RecapCard.Fastest -> PaletteEntry.Fastest
    is RecapCard.Streak -> PaletteEntry.Streak
    is RecapCard.Moments -> PaletteEntry.Moments
    is RecapCard.Friends -> PaletteEntry.Friends
    is RecapCard.TotalTime -> PaletteEntry.TotalTime
    is RecapCard.Month -> PaletteEntry.YearMonth
}

/** The palette entry of what the deck shows: the cover, a card, or the Share page that follows the last card. */
internal fun recapPalette(cards: List<RecapCard>, deck: RecapDeckState): PaletteEntry = when {
    deck.onCover -> PaletteEntry.Cover
    deck.page < cards.size -> cards[deck.page].palette()
    else -> PaletteEntry.Share
}
