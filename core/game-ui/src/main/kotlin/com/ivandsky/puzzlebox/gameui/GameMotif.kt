package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * [brand]'s glyph as a card's background motif (hub card handoff §Motif): drawn at [size]
 * with [stroke] on the 24 grid, pinned to [alignment] of the space it fills and moved by
 * [offset] (x toward the end, y down), so it overflows and the card's clip crops it.
 * [tint] at [alpha] applies to the motif as one layer, so crossing strokes don't darken.
 */
@Composable
fun GameMotif(
    brand: GameBrand,
    size: Dp,
    stroke: Float,
    alignment: Alignment,
    offset: DpOffset,
    tint: Color,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val motif = remember(brand.glyph, stroke) { brand.glyph.withStroke(stroke) }
    Icon(
        imageVector = motif,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(alignment, unbounded = true)
            .offset(offset.x, offset.y)
            .size(size)
            .alpha(alpha),
    )
}

/**
 * The same vector with every stroked path redrawn at [width]. Rebuilding the paths keeps
 * each one's own caps, joins, fills and group transforms, which a Canvas `Stroke` over
 * the path data would have to restate by hand.
 */
internal fun ImageVector.withStroke(width: Float): ImageVector {
    val builder = ImageVector.Builder(
        name = "$name.Motif",
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        tintColor = tintColor,
        tintBlendMode = tintBlendMode,
        autoMirror = autoMirror,
    )
    builder.addChildren(root, width)
    return builder.build()
}

private fun ImageVector.Builder.addChildren(group: VectorGroup, width: Float) {
    group.forEach { node ->
        when (node) {
            is VectorPath -> addPath(
                pathData = node.pathData,
                pathFillType = node.pathFillType,
                name = node.name,
                fill = node.fill,
                fillAlpha = node.fillAlpha,
                stroke = node.stroke,
                strokeAlpha = node.strokeAlpha,
                strokeLineWidth = if (node.stroke == null) node.strokeLineWidth else width,
                strokeLineCap = node.strokeLineCap,
                strokeLineJoin = node.strokeLineJoin,
                strokeLineMiter = node.strokeLineMiter,
                trimPathStart = node.trimPathStart,
                trimPathEnd = node.trimPathEnd,
                trimPathOffset = node.trimPathOffset,
            )
            is VectorGroup -> {
                addGroup(
                    name = node.name,
                    rotate = node.rotation,
                    pivotX = node.pivotX,
                    pivotY = node.pivotY,
                    scaleX = node.scaleX,
                    scaleY = node.scaleY,
                    translationX = node.translationX,
                    translationY = node.translationY,
                    clipPathData = node.clipPathData,
                )
                addChildren(node, width)
                clearGroup()
            }
        }
    }
}

// Preview: the hero crop (210dp, stroke 1.4, bottom-end) and the tile crop (132dp,
// stroke 1.6, top-end, plus its offline tint) at the handoff's card sizes.
@PuzzleBoxPreviews
@Composable
internal fun GameMotifPreview() {
    PuzzleBoxTheme {
        val brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal)
        val online = gameCardColors(brand, offline = false)
        val offline = gameCardColors(brand, offline = true)
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(Modifier.size(380.dp, 152.dp).clip(AppShapes.extraLarge).background(online.fill)) {
                GameMotif(
                    brand, 210.dp, 1.4f, Alignment.BottomEnd, DpOffset(34.dp, 54.dp),
                    online.motif, online.motifAlpha,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                listOf(online, offline).forEach { colors ->
                    Box(Modifier.size(184.dp, 156.dp).clip(AppShapes.large).background(colors.fill)) {
                        GameMotif(
                            brand, 132.dp, 1.6f, Alignment.TopEnd, DpOffset(30.dp, (-26).dp),
                            colors.motif, colors.motifAlpha,
                        )
                    }
                }
            }
        }
    }
}
