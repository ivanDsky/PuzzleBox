package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.gameui.GameBrand
import kotlin.math.abs
import kotlin.math.sign

/** How much of the accent's container the wash is; the rest is the theme's background. */
private const val WASH = 0.6f

/** How much of the hub's rows and cards is their own surface; the wash shows through the rest. */
internal const val WORLD_SURFACE_ALPHA = 0.84f

private const val GLYPH_ALPHA = 0.22f
private const val GLYPH_TURN = 16f
private val GlyphSize = 330.dp
private val GlyphSizeWide = 480.dp
private val GlyphDrift = 130.dp
private val GlyphDriftWide = 200.dp
private val GlyphOutset = 50.dp
private val GlyphLift = 40.dp
private val GlyphOutsetWide = 30.dp
private val GlyphLiftWide = (-30).dp

/**
 * The hub's world: [content] on a wash of the shown board's accent ([brands] in swipe order), with the board's glyph
 * drawn large and faint behind it at the bottom end. Between two boards ([scrub]) the wash is the blend of their
 * colours by the scrub's progress, and the two glyphs trade places, drifting and turning a little slower than the
 * boards do. All of it is drawn from the progress, so a scrub recomposes nothing here. [wide] is the tablet's two
 * panes: a larger glyph that drifts further. A board with no brand of its own is neutral: the theme's own background.
 */
@Composable
internal fun RanksWorld(brands: List<GameBrand>, scrub: BoardScrub, wide: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    val washes = brands.map { lerp(background, it.accent.colors().container, WASH) }
    Box(
        modifier
            .drawBehind { drawRect(scrub.blend(washes, background)) }
            .clipToBounds(),
    ) {
        val from = scrub.from
        val to = scrub.to
        val way = (to - from).sign.toFloat()
        brands.getOrNull(from)?.let { GhostGlyph(it, wide) { -scrub.progress * way } }
        if (to != from) brands.getOrNull(to)?.let { GhostGlyph(it, wide) { (1f - scrub.progress) * way } }
        content()
    }
}

/** One glyph of the world, [away] of its drift from its resting place (0 there, ±1 gone; read while drawing). */
@Composable
private fun BoxScope.GhostGlyph(brand: GameBrand, wide: Boolean, away: () -> Float) {
    val drift = if (wide) GlyphDriftWide else GlyphDrift
    Icon(
        brand.glyph,
        contentDescription = null,
        tint = brand.accent.colors().accent,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = if (wide) GlyphOutsetWide else GlyphOutset, y = -(if (wide) GlyphLiftWide else GlyphLift))
            .size(if (wide) GlyphSizeWide else GlyphSize)
            .graphicsLayer {
                val by = away()
                alpha = GLYPH_ALPHA * (1f - abs(by))
                translationX = by * drift.toPx()
                rotationZ = by * GLYPH_TURN
            },
    )
}

@PuzzleBoxPreviews
@Composable
private fun RanksWorldPreview() {
    PuzzleBoxTheme {
        val brands = listOf(GameBrand.Neutral, GameBrand(AccentSlot.Blue, GameGlyphs.Zip))
        RanksWorld(brands, remember { BoardScrub(1) }, wide = false, Modifier.fillMaxSize()) {
            Text("Zip · today", Modifier.padding(Spacing.lg), style = MaterialTheme.typography.titleLarge)
        }
    }
}
