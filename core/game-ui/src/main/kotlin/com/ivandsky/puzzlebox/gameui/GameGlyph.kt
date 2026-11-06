package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.LocalDesignVariants
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors

private const val GLYPH_GRID = 24f
private const val GLYPH_STROKE = 2f

/**
 * A game's glyph in its accent (identity §2.5): outlined in the accent on the accent
 * container, or, when [solid], in onAccent (white in light) on the accent itself.
 * [solid] defaults to the glyph style in [LocalDesignVariants] (outlined in release).
 * [GameGlyphSize.Inline] draws the bare glyph in the accent, with no well.
 */
@Composable
fun GameGlyph(
    brand: GameBrand,
    size: GameGlyphSize,
    modifier: Modifier = Modifier,
    solid: Boolean = LocalDesignVariants.current.glyphSolid,
) {
    val accent = brand.accent.colors()
    if (size.box == size.glyph) {
        Icon(brand.glyph, contentDescription = null, modifier = modifier.size(size.glyph), tint = accent.accent)
        return
    }
    Box(
        modifier = modifier
            .size(size.box)
            .background(if (solid) accent.accent else accent.container, RoundedCornerShape(size.corner)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = brand.glyph,
            contentDescription = null,
            modifier = Modifier.size(size.glyph),
            tint = if (solid) accent.onAccent else accent.accent,
        )
    }
}

/**
 * Builds a game glyph on the 24 grid in the icon grammar (identity §2.5): [stroke] is
 * SVG path data drawn with a 2dp round stroke; [fill] is path data both filled and
 * stroked (the dots ≤5dp). Colour comes from the [GameGlyph]/Icon tint.
 */
fun gameGlyph(name: String, stroke: String, fill: String = ""): ImageVector {
    val builder = ImageVector.Builder(
        name = "GameGlyph.$name",
        defaultWidth = GLYPH_GRID.dp,
        defaultHeight = GLYPH_GRID.dp,
        viewportWidth = GLYPH_GRID,
        viewportHeight = GLYPH_GRID,
    )
    builder.addPath(
        pathData = addPathNodes(stroke),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = GLYPH_STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    if (fill.isNotEmpty()) {
        builder.addPath(
            pathData = addPathNodes(fill),
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = GLYPH_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

// Preview: every size in both styles (tinted, then solid; Inline → Hero), for the
// neutral brand and one accent slot.
@PuzzleBoxPreviews
@Composable
internal fun GameGlyphPreview() {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(GameBrand.Neutral, GameBrand.Neutral.copy(accent = AccentSlot.Teal)).forEach { brand ->
                listOf(false, true).forEach { solid ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameGlyphSize.entries.forEach { size -> GameGlyph(brand, size, solid = solid) }
                    }
                }
            }
        }
    }
}
