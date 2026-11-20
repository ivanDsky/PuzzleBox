package com.ivandsky.puzzlebox.insights.share

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.RecapCard
import com.ivandsky.puzzlebox.designsystem.RecapCardColors
import com.ivandsky.puzzlebox.designsystem.recapCardColors

/** The three looks of the share poster. The player picks one; it is a parameter of the image, not part of the recap's data. */
internal enum class ShareStyle { Classic, Colour, Night }

/**
 * What a poster paints with: the [ground], the [ink] of the title and name, the [hero] number's colour, the [wordmark]'s,
 * the [track] the split bar sits on, and for Night the [holo] colours of the layer over everything. [tilesDark] picks the
 * theme the tiles' own card colours come from.
 */
internal data class SharePalette(
    val ground: Color,
    val ink: Color,
    val hero: Color,
    val wordmark: Color,
    val track: Color,
    val tilesDark: Boolean,
    val holo: List<Color> = emptyList(),
) {
    /** The Field, On<Card> and plate of [card], the colours a tile paints with. */
    fun tile(card: RecapCard): RecapCardColors = recapCardColors(card, tilesDark)
}

/**
 * The palette of [style]. Classic is the Share card and follows the app theme ([dark]). Colour and Night are fixed: a
 * shared image does not change with the viewer's theme. Colour is the Cover card's light values. Night grounds on the
 * Year month ink, inks in the Year month field, with the Solved field for the hero and the Cover field for the
 * wordmark. Every colour is a palette token; nothing is added.
 */
internal fun sharePalette(style: ShareStyle, dark: Boolean): SharePalette = when (style) {
    ShareStyle.Classic -> recapCardColors(RecapCard.Share, dark).let { SharePalette(it.field, it.ink, it.ink, it.ink, it.plate, dark) }
    ShareStyle.Colour -> recapCardColors(RecapCard.Cover, false).let { SharePalette(it.field, it.ink, it.ink, it.ink, it.plate, false) }
    ShareStyle.Night -> {
        val month = recapCardColors(RecapCard.YearMonth, false)
        SharePalette(
            ground = month.ink,
            ink = month.field,
            hero = recapCardColors(RecapCard.Solved, false).field,
            wordmark = recapCardColors(RecapCard.Cover, false).field,
            track = month.field.copy(alpha = TrackAlpha),
            tilesDark = false,
            holo = listOf(RecapCard.Streak, RecapCard.Fastest, RecapCard.Cover, RecapCard.Solved, RecapCard.Fastest)
                .map { recapCardColors(it, false).field.copy(alpha = HoloAlpha) },
        )
    }
}

/**
 * Paints [colors] as a diagonal sheen over what is drawn: Night's holographic layer. It is a still in the captured image;
 * only the page's deck moves it, by [shift] (a share of the width, read when drawing), and it is 0 whenever a poster rests.
 */
internal fun Modifier.holo(colors: List<Color>, shift: () -> Float = { 0f }): Modifier =
    if (colors.isEmpty()) this else drawWithContent {
        drawContent()
        val dx = shift() * size.width
        drawRect(Brush.linearGradient(colors, start = Offset(dx, 0f), end = Offset(size.width + dx, size.height)))
    }

private const val TrackAlpha = 0.16f
private const val HoloAlpha = 0.18f
