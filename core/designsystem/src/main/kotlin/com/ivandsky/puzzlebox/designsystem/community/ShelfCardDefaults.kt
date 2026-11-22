package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import java.text.NumberFormat
import java.util.Locale

/**
 * The shared dims, press values and copy rules of the community cards (shelf, spotlight, entry tile,
 * make card), from the shelves prototype handoff (#2012, B1+B2). Colours are theme roles, so the only
 * new tokens here are the card dims; no card is pill shaped.
 */
object ShelfCardDefaults {
    /** A shelf card: 156dp wide over a 120dp art with [CardCorner] corners. */
    val CardWidth: Dp = 156.dp
    val ArtHeight: Dp = 120.dp
    val CardCorner: Dp = 20.dp

    /** The game disc in the art of a shelf card. */
    val DiscSize: Dp = 56.dp

    /** The spotlight: a 112dp art square with a 64dp disc, on a 24dp-cornered card. */
    val SpotlightArtSize: Dp = 112.dp
    val SpotlightArtCorner: Dp = 18.dp
    val SpotlightDiscSize: Dp = 64.dp
    val SpotlightCorner: Dp = 24.dp

    /** An entry tile is a link, 64dp at the least, with the card corner. */
    val EntryTileMinHeight: Dp = 64.dp

    /** The make card: 132dp on a shelf with puzzles, 200dp in the make row or an empty shelf. */
    val MakeCardWidth: Dp = 132.dp
    val MakeRowCardWidth: Dp = 200.dp
    val MakeIconTile: Dp = 36.dp
    val MakeDashWidth: Dp = 1.5.dp

    /** The ring on the card open in the tablet's detail pane. */
    val SelectionRing: Dp = 3.dp

    /** The art is the game colour at [ArtTint] over the quiet surface, with a dot pattern at [PatternAlpha]. */
    const val ArtTint = 0.26f
    const val PatternAlpha = 0.22f
    val PatternStep: Dp = 14.dp
    val PatternDot: Dp = 1.5.dp

    /** The scrim behind the solves chip on art, with white ink on top. */
    val Scrim: Color = Color(0xCC0B0D10)

    /** Press scale: .97 for cards, .985 for the large spotlight card; 120ms in and out. */
    const val PressScale = MotionTokens.PressScale
    const val SpotlightPressScale = 0.985f
    const val PressMillis = 120

    /** The spotlight's kicker. */
    const val SpotlightKicker = "#1 MOST PLAYED"

    /** The scale a card settles at: pressed shrinks to [scale], rest and reduced motion stay at full size. */
    internal fun pressTarget(pressed: Boolean, reduced: Boolean, scale: Float): Float = if (pressed && !reduced) scale else 1f

    /** Reduced motion collapses the press to no tween at all. */
    internal fun pressMillis(reduced: Boolean): Int = if (reduced) 0 else PressMillis

    /** A solve count with its thousands grouped for [locale]. */
    internal fun solvesText(count: Int, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(count)

    /** The line under a shelf card's title: "Author · Difficulty", or the author alone. */
    internal fun byline(author: String, difficulty: String?): String = listOfNotNull(author, difficulty).joinToString(SEPARATOR)

    /** The spotlight's line: "Game · Difficulty · by Author", the difficulty only when there is one. */
    internal fun spotlightLine(gameName: String, difficulty: String?, author: String): String =
        listOfNotNull(gameName, difficulty, "by $author").joinToString(SEPARATOR)

    /** The make card's words. */
    internal fun makeLabel(gameName: String): String = "Make a $gameName puzzle"

    private const val SEPARATOR = " · "
}
